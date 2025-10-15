package com.arbitrage.service.validators.guard;

import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.MetaDto;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaIntegrityServiceImpl implements MetaIntegrityService {

  private final Clock clock;

  @Override
  public StepResult validate(MetaDto meta) {
    if (meta == null) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing meta")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: missing meta");
      return StepResult.fail(rej);
    }
    if (isBlank(meta.getSignalId())) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing meta.signalId")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: missing meta.signalId");
      return StepResult.fail(rej);
    }
    if (meta.getCreatedAt() == null) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing meta.createdAt")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: signalId={}, missing meta.createdAt", meta.getSignalId());
      return StepResult.fail(rej);
    }
    if (meta.getTtlMs() == null || meta.getTtlMs() <= 0) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing/invalid meta.ttlMs")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: signalId={}, missing/invalid meta.ttlMs", meta.getSignalId());
      return StepResult.fail(rej);
    }
    if (isBlank(meta.getPair())) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing meta.pair")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: signalId={}, missing meta.pair", meta.getSignalId());
      return StepResult.fail(rej);
    }
    // maxLatencyMs is optional; if present it must be > 0
    if (meta.getMaxLatencyMs() != null && meta.getMaxLatencyMs() <= 0) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Invalid meta.maxLatencyMs")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: signalId={}, invalid meta.maxLatencyMs", meta.getSignalId());
      return StepResult.fail(rej);
    }

    log.info("Meta integrity ok: signalId={}, pair={}", meta.getSignalId(), meta.getPair());
    return StepResult.ok();
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
