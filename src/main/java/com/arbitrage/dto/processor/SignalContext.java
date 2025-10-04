package com.arbitrage.dto.processor;

import com.arbitrage.dto.SignalMessageDto;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Immutable processing context passed across validation steps. Carries: - The original normalized
 * DTO - A stable 'now' timestamp (injected from Clock for testability) - The saved DB id (after
 * Phase-0 persist) - An extensible attributes bag for cross-step hints (optional)
 */
@Value
@Builder(toBuilder = true)
public class SignalContext {

  // The original normalized inbound signal
  SignalMessageDto dto;

  // A stable 'now' used for TTL/latency calculations
  Instant now;

  // DB id of the persisted signal (created in Phase-0)
  UUID savedSignalId;

  // Optional attributes bag (useful to pass intermediate hints/results)
  @Singular("attr")
  Map<String, Object> attributes;

  /**
   * Factory method to create context using an injected Clock. Keeps 'now' stable for the entire
   * processing pipeline.
   */
  public static SignalContext of(SignalMessageDto dto, Clock clock, UUID savedId) {
    return SignalContext.builder().dto(dto).now(clock.instant()).savedSignalId(savedId).build();
  }

  /** Retrieve an attribute by key as Optional. */
  public Optional<Object> attr(String key) {
    return Optional.ofNullable(attributes == null ? null : attributes.get(key));
  }

  /**
   * Create a shallowly updated copy with a new attribute. This preserves immutability while
   * enabling step-to-step enrichment.
   */
  public SignalContext withAttr(String key, Object value) {
    return this.toBuilder().attr(key, value).build();
  }
}
