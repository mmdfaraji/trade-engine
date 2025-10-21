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
              .code(RejectCode.INTEGRITY_MISSING_META)
              .message("Missing meta")
              .phase(ValidationPhase.PHASE0_INTEGRITY)
              .validator("MetaIntegrityService")
              .occurredAt(clock.instant())
              .build();
      log.warn("Integrity failed: missing meta");
      return StepResult.fail(rej);
    }
    if (isBlank(meta.getSignalId())) {
      return fail("Missing meta.signalId", RejectCode.INTEGRITY_MISSING_FIELD);
    }
    if (meta.getCreatedAt() == null) {
      return fail("Missing meta.createdAt", RejectCode.INTEGRITY_MISSING_FIELD);
    }
    if (meta.getTtlMs() == null || meta.getTtlMs() <= 0) {
      return fail("Missing/invalid meta.ttlMs", RejectCode.INTEGRITY_INVALID_VALUE);
    }
    if (isBlank(meta.getPair())) {
      return fail("Missing meta.pair", RejectCode.INTEGRITY_MISSING_FIELD);
    }
    if (meta.getMaxLatencyMs() != null && meta.getMaxLatencyMs() <= 0) {
      return fail("Invalid meta.maxLatencyMs", RejectCode.INTEGRITY_INVALID_VALUE);
    }

    log.info("Meta integrity ok: signalId={}, pair={}", meta.getSignalId(), meta.getPair());
    return StepResult.ok();
  }

  private StepResult fail(String msg, RejectCode code) {
    Rejection rej =
        Rejection.builder()
            .code(code)
            .message(msg)
            .phase(ValidationPhase.PHASE0_INTEGRITY)
            .validator("MetaIntegrityService")
            .occurredAt(clock.instant())
            .build();
    log.warn("Integrity failed: {}", msg);
    return StepResult.fail(rej);
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
