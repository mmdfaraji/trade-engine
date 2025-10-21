package com.arbitrage.dto.processor;

import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Immutable rejection descriptor used by StepResult and audit events. Use builder
 * with @Singular("detail") to add details:
 *
 * <p>Rejection r = Rejection.builder() .code(RejectCode.STALE) .message("Signal expired")
 * .phase(ValidationPhase.PHASE1_FRESHNESS) .validator("SignalValidator")
 * .occurredAt(clock.instant()) .detail("age_ms", age) .detail("ttl_ms", ttl) .build();
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rejection {
  private final RejectCode code;
  private final String message;

  private final ValidationPhase phase; // which phase failed
  private final String validator; // component name that produced the rejection

  private final Instant occurredAt; // wall clock at the time of rejection

  @Singular("detail")
  private final Map<String, Object> details; // structured extras for diagnostics (safe to JSON)
}
