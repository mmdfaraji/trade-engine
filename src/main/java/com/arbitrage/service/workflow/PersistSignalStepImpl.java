package com.arbitrage.service.workflow;

import com.arbitrage.dto.processor.PersistSignalResult;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalService;
import com.arbitrage.service.workflow.steps.PersistSignalStep;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Persists the signal and maps exceptions to ProcessResult. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistSignalStepImpl implements PersistSignalStep {

  private final SignalService signalService;
  private final Clock clock;

  @Override
  public PersistSignalResult execute(TradeSignalDto dto) {
    try {
      UUID id = signalService.saveSignal(dto);
      return PersistSignalResult.continued(id);
    } catch (IllegalArgumentException iae) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.REFERENCE_NOT_FOUND)
              .message(iae.getMessage())
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(clock.instant())
              .build();
      log.warn(
          "Persist rejected: signalId={}, reason={}",
          dto.getMeta() != null ? dto.getMeta().getSignalId() : "n/a",
          iae.getMessage());
      return PersistSignalResult.terminated(ProcessResult.rejected(null, List.of(rej)));
    } catch (Exception ex) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INTERNAL_ERROR)
              .message("Unexpected error at persist step")
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(clock.instant())
              .detail("error", ex.getMessage())
              .build();
      log.error(
          "Persist error (transient): signalId={}, err={}",
          dto.getMeta() != null ? dto.getMeta().getSignalId() : "n/a",
          ex.getMessage(),
          ex);
      return PersistSignalResult.terminated(ProcessResult.retryTransient(List.of(rej)));
    }
  }
}
