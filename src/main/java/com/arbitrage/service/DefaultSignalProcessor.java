package com.arbitrage.service;

import com.arbitrage.dto.balance.BalanceValidationReportDto;
import com.arbitrage.dto.processor.PersistSignalResult;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalEventService;
import com.arbitrage.service.api.SignalProcessor;
import com.arbitrage.service.api.SignalValidator;
import com.arbitrage.service.workflow.steps.DecisionService;
import com.arbitrage.service.workflow.steps.PersistLegsStep;
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
  private final SignalEventService signalEventService;
  private final PersistLegsStep persistLegsStep;
  private final SignalValidator signalValidator;
  private final DecisionService decisionService;
  private final Clock clock;

  // start line of new codes
  @Override
  public ProcessResult process(TradeSignalDto dto) {

    // 0) Persist signal (idempotent) — RECEIVED event will save on PersistSignalStep
    PersistSignalResult persist = persistSignalStep.execute(dto);
    if (persist.getStop().isPresent()) {
      return persist.getStop().get();
    }
    log.info(
        "Trade signal saved: stored signalId {} and externalId {}",
        persist.getId(),
        dto.getMeta().getSignalId());
    UUID savedId = persist.getId();
    SignalContext ctx = SignalContext.of(dto, clock, savedId);

    // 1) Integrity
    StepResult integrityResult = signalValidator.validateIntegrity(ctx);
    Optional<ProcessResult> integrityDecision =
        decisionService.handle(ctx, integrityResult, ValidationPhase.PHASE0_INTEGRITY);
    if (integrityDecision.isPresent()) {
      return integrityDecision.get();
    }
    signalEventService.recordOk(savedId, ValidationPhase.PHASE0_INTEGRITY);
    log.info("integrity passed: signalId={}", savedId);

    // 2) Persist normalized legs (after integrity)
    StepResult persistLegsResult = persistLegsStep.execute(ctx);
    Optional<ProcessResult> persistLegsDecision =
        decisionService.handle(ctx, persistLegsResult, ValidationPhase.PHASE0_PERSIST);
    if (persistLegsDecision.isPresent()) {
      return persistLegsDecision.get();
    }
    signalEventService.recordOk(savedId, ValidationPhase.PHASE0_PERSIST);
    log.info("legs persisted: signalId={}", savedId);

    // 3) Freshness (ttl / latency)
    StepResult freshnessResult = signalValidator.validateFreshness(ctx);
    Optional<ProcessResult> freshnessDecision =
        decisionService.handle(ctx, freshnessResult, ValidationPhase.PHASE1_FRESHNESS);
    if (freshnessDecision.isPresent()) {
      return freshnessDecision.get();
    }
    signalEventService.recordOk(savedId, ValidationPhase.PHASE1_FRESHNESS);
    log.info("freshness passed: signalId={}", savedId);

    // 4) Balance (read-only, no reservation)
    BalanceValidationReportDto balanceReport = signalValidator.validateBalance(ctx);
    Optional<ProcessResult> balanceDecision =
        decisionService.handle(ctx, balanceReport.getResult(), ValidationPhase.PHASE2_BALANCE);
    if (balanceDecision.isPresent()) {
      return balanceDecision.get();
    }
    signalEventService.recordOk(savedId, ValidationPhase.PHASE2_BALANCE);
    log.info("balance passed: signalId={}", savedId);

    signalEventService.recordAccepted(savedId);
    log.info("accepted: signalId={}", savedId);
    return ProcessResult.accepted(savedId);
  }
  // end line of new codes

}
