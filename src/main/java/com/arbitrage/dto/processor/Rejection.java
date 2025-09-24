package com.arbitrage.dto.processor;

import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.*;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rejection {
  RejectCode code; // canonical machine-friendly code
  String message; // short human-readable reason (for logs)
  ValidationPhase phase; // where it failed in the pipeline
  String validator; // simple class name or step id (e.g., "FreshnessValidator")
  Integer legIndex; // optional: leg index if the failure is leg-specific
  Instant occurredAt; // when the rejection was produced (UTC)

  @Singular("detail")
  Map<String, Object> details; // structured context for analysis (key/value)
}
