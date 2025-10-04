package com.arbitrage.service;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalProcessor;
import com.arbitrage.service.api.SignalService;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.arbitrage.service.validators.api.FreshnessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSignalProcessor implements SignalProcessor {
  private final SignalService signalService;
  private final FreshnessValidator freshnessValidator;
  private final Clock clock;

  @Override
  public ProcessResult process(SignalMessageDto dto) {
    UUID id;
    try {
      id = signalService.saveSignal(dto);
    } catch (IllegalArgumentException iae) {
      // Reference not found (exchange/pair) -> logical reject, ACK
      Rejection rej = Rejection.builder()
              .code(RejectCode.REFERENCE_NOT_FOUND)
              .message(iae.getMessage())
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Signal rejected: code={}, externalId={}, reason={}",
              rej.getCode(), dto.getSignalId(), iae.getMessage());
      return ProcessResult.rejected(null, List.of(rej));
    } catch (Exception ex) {
      Rejection rej = Rejection.builder()
              .code(RejectCode.INTERNAL_ERROR)
              .message("Unexpected error at persist phase")
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(clock.instant())
              .detail("error", ex.getMessage())
              .build();
      log.error("internal error: externalId={}, err={}", dto.getSignalId(), ex.getMessage(), ex);
      return ProcessResult.retryTransient(List.of(rej));
    }

    // Build context for validations
    SignalContext ctx = SignalContext.of(dto, clock, id);

    // Phase 1: Freshness / Latency
    StepResult fresh = freshnessValidator.validate(ctx);
    if (!fresh.isOk()) {
      // Update status to REJECTED (audit-friendly)
      try {
        signalService.updateStatus(id, SignalStatus.REJECTED);
      } catch (Exception e) {
        log.warn("Status update to REJECTED failed: signalId={}", id, e);
      }
      List<Rejection> rej = fresh.getRejection().map(List::of).orElse(List.of());
      log.warn("Phase-1 reject: externalId={}, signalId={}, reason={}",
              dto.getSignalId(), id, rej.isEmpty() ? "n/a" : rej.get(0).getMessage());
      return ProcessResult.rejected(id, rej);
    }

    // If passed, we keep going in next phases (not implemented yet here)
    log.info("Phase-1 passed (freshness): externalId={}, signalId={}", dto.getSignalId(), id);
    return ProcessResult.accepted(id);
  }
}
