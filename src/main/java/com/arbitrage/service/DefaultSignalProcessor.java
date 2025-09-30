package com.arbitrage.service;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalProcessor;
import com.arbitrage.service.api.SignalService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSignalProcessor implements SignalProcessor {
  private final SignalService signalService;

  @Override
  public ProcessResult process(SignalMessageDto dto) {
    try {
      UUID id = signalService.saveSignal(dto);
      int legsCount = dto.getLegs() == null ? 0 : dto.getLegs().size();
      log.info(
          "Phase-0 persisted: signalId={}, externalId={}, legsCount={}",
          id,
          dto.getSignalId(),
          legsCount);
      return ProcessResult.accepted(id);
    } catch (IllegalArgumentException iae) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.REFERENCE_NOT_FOUND)
              .message(iae.getMessage())
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(java.time.Instant.now())
              .build();

      log.warn(
          "reject: code={}, externalId={}, reason={}",
          rej.getCode(),
          dto.getSignalId(),
          iae.getMessage());

      return ProcessResult.rejected(null, java.util.List.of(rej));
    } catch (Exception ex) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INTERNAL_ERROR)
              .message("Unexpected error at persist phase")
              .phase(ValidationPhase.PHASE0_PERSIST)
              .validator("SignalService")
              .occurredAt(java.time.Instant.now())
              .detail("error", ex.getMessage())
              .build();

      log.error("internal error: externalId={}, err={}", dto.getSignalId(), ex.getMessage(), ex);
      return ProcessResult.retryTransient(java.util.List.of(rej));
    }
  }
}
