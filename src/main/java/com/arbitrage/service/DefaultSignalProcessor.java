package com.arbitrage.service;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalProcessor;
import com.arbitrage.service.api.SignalValidator;
import com.arbitrage.service.workflow.steps.DecisionService;
import com.arbitrage.service.workflow.steps.PersistSignalStep;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the pipeline without performing any side-effectful reservation. Steps: 0) Persist
 * (via PersistSignalStep) 1) Freshness validation (SignalValidator) + decision step 2) Balance
 * sufficiency validation (read-only) + decision step If all pass, returns
 * ProcessResult.accepted(...) with saved signalId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSignalProcessor implements SignalProcessor {

  private final PersistSignalStep persistSignalStep;
  private final SignalValidator signalValidator;
  private final DecisionService decisionService;
  private final Clock clock;

  @Override
  public ProcessResult process(TradeSignalDto dto) {

    // 0) Persist
    var persist = persistSignalStep.execute(dto);
    if (persist.getStop().isPresent()) {
      return persist.getStop().get();
    }
    UUID savedId = persist.getId();
    SignalContext ctx = SignalContext.of(dto, clock, savedId);

    // integrity
    StepResult integrityResult = signalValidator.validateIntegrity(ctx);
    Optional<ProcessResult> integrityDecision =
        decisionService.handle(ctx, integrityResult, ValidationPhase.PHASE0_INTEGRITY);
    if (integrityDecision.isPresent()) {
      return integrityDecision.get();
    }
    log.info("Integrity check passed: signalId={}", savedId);

    // freshness
    StepResult freshnessResult = signalValidator.validateFreshness(ctx);
    Optional<ProcessResult> freshnessDecision =
        decisionService.handle(ctx, freshnessResult, ValidationPhase.PHASE1_FRESHNESS);
    if (freshnessDecision.isPresent()) {
      return freshnessDecision.get();
    }
    log.info("Freshness check passed: signalId={}", savedId);

    // balance (read-only)
    var balanceReport = signalValidator.validateBalance(ctx);
    Optional<ProcessResult> balanceDecision =
        decisionService.handle(ctx, balanceReport.getResult(), ValidationPhase.PHASE2_BALANCE);
    if (balanceDecision.isPresent()) {
      return balanceDecision.get();
    }
    log.info("Balance check passed: signalId={}", savedId);

    return ProcessResult.accepted(savedId);
  }
}
