package com.arbitrage.service;

import com.arbitrage.dto.plan.BalanceReservationResultDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalProcessor;
import com.arbitrage.service.api.SignalService;
import com.arbitrage.service.balance.BalanceReservationService;
import com.arbitrage.service.validators.api.FreshnessValidator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSignalProcessor implements SignalProcessor {
  private final SignalService signalService;
  private final FreshnessValidator freshnessValidator;
  private final BalanceReservationService balanceReservationService;
  private final Clock clock;

  @Override
  public ProcessResult process(SignalMessageDto dto) {

    UUID id;
    try {
      id = signalService.saveSignal(dto);
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
          "Persist step rejected: externalId={}, reason={}", dto.getSignalId(), iae.getMessage());
      return ProcessResult.rejected(null, List.of(rej)); // ACK
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
          "Persist step error (transient): externalId={}, err={}",
          dto.getSignalId(),
          ex.getMessage(),
          ex);
      return ProcessResult.retryTransient(List.of(rej)); // NO_ACK
    }

    // Build context
    SignalContext ctx = SignalContext.of(dto, clock, id);

    // Freshness/Latency step
    StepResult fresh = freshnessValidator.validate(ctx);
    if (!fresh.isOk()) {
      // Mark as REJECTED (best-effort)
      try {
        signalService.updateStatus(id, SignalStatus.REJECTED);
      } catch (Exception e) {
        log.warn("Status update to REJECTED failed: signalId={}", id, e);
      }

      List<Rejection> rejections = fresh.getRejection().map(List::of).orElse(List.of());
      log.warn(
          "Rejected at freshness: externalId={}, signalId={}, code={}, msg={}",
          dto.getSignalId(),
          id,
          rejections.isEmpty() ? "n/a" : rejections.get(0).getCode(),
          rejections.isEmpty() ? "n/a" : rejections.get(0).getMessage());

      return ProcessResult.rejected(id, rejections); // ACK
    }

    log.info("Freshness check passed: externalId={}, signalId={}", dto.getSignalId(), id);

    BalanceReservationResultDto br = balanceReservationService.reserveForSignal(ctx);
    if (!br.getResult().isOk()) {
      try {
        signalService.updateStatus(id, SignalStatus.REJECTED);
      } catch (Exception e) {
        log.warn("Status update to REJECTED failed: signalId={}", id, e);
      }
      java.util.List<Rejection> reasons =
          br.getResult().getRejection().map(java.util.List::of).orElse(java.util.List.of());
      log.warn(
          "Rejected at balance/reservation: externalId={}, signalId={}, code={}, msg={}",
          dto.getSignalId(),
          id,
          reasons.isEmpty() ? "n/a" : reasons.get(0).getCode(),
          reasons.isEmpty() ? "n/a" : reasons.get(0).getMessage());
      return ProcessResult.rejected(id, reasons);
    }

    ExecutionPlanDto plan = br.getPlan();
    log.info(
        "Balance & reservation passed: externalId={}, signalId={}, legs={}",
        dto.getSignalId(),
        id,
        plan.getLegs().size());

    return ProcessResult.accepted(id);
  }
}
