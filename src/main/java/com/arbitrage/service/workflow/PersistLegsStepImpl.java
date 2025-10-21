package com.arbitrage.service.workflow;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalLegService;
import com.arbitrage.service.workflow.steps.PersistLegsStep;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistLegsStepImpl implements PersistLegsStep {

  private final SignalLegService signalLegService;
  private final Clock clock;

  @Override
  public StepResult execute(SignalContext ctx) {
    try {
      List<ResolvedLegDto> resolved = ctx.getResolvedLegs();
      signalLegService.persistResolved(ctx.getSavedSignalId(), resolved, ctx.getDto().getOrders());

      log.info(
          "legs persisted: signalId={}, count={}",
          ctx.getSavedSignalId(),
          resolved == null ? 0 : resolved.size());

      return StepResult.ok();
    } catch (Exception e) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.TRANSIENT_UPSTREAM)
              .message("Persist legs failed")
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("PersistLegsStep")
              .occurredAt(clock.instant())
              .detail("error", e.getMessage())
              .build();

      log.error(
          "persist legs failed: signalId={}, err={}", ctx.getSavedSignalId(), e.getMessage(), e);

      return StepResult.fail(rej);
    }
  }
}
