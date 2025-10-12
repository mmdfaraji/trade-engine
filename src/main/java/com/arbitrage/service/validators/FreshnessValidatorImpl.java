/*
package com.arbitrage.service.validators;

import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.service.mapping.Rejections;
import com.arbitrage.service.validators.api.FreshnessValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessValidatorImpl implements FreshnessValidator {

  private final Clock clock;

  @Override
  public StepResult validate(SignalContext ctx) {
    // --- Use producer timestamp and TTL/Latency from DTO ---
    final SignalMessageDto dto = ctx.getDto();
    final Instant now = ctx.getNow() != null ? ctx.getNow() : clock.instant();

    // createdAt must be present
    if (dto.getCreatedAt() == null) {
      Rejection rej = Rejections.missingCreatedAt(clock, dto.getSignalId());
      log.warn(
          "Freshness check rejected: externalId={}, signalId={}, reason={}",
          dto.getSignalId(),
          ctx.getSavedSignalId(),
          rej.getMessage());
      return StepResult.fail(rej);
    }

    // ttlMs must be present and positive
    Long ttlMs = dto.getTtlMs();
    if (ttlMs == null) {
      Rejection rej = Rejections.missingTtl(clock, dto.getSignalId());
      log.warn(
          "Freshness check rejected: externalId={}, signalId={}, reason={}",
          dto.getSignalId(),
          ctx.getSavedSignalId(),
          rej.getMessage());
      return StepResult.fail(rej);
    }
    if (ttlMs <= 0L) {
      Rejection rej = Rejections.nonPositiveTtl(clock, dto.getSignalId(), ttlMs);
      log.warn(
          "Freshness check rejected: externalId={}, signalId={}, reason={}, ttlMs={}",
          dto.getSignalId(),
          ctx.getSavedSignalId(),
          rej.getMessage(),
          ttlMs);
      return StepResult.fail(rej);
    }

    long ageMs = Duration.between(dto.getCreatedAt(), now).toMillis();

    // TTL check
    if (ageMs > ttlMs) {
      Rejection rej = Rejections.stale(clock, ageMs, ttlMs, dto.getCreatedAt(), now);
      log.warn(
          "Freshness check rejected: externalId={}, signalId={}, ageMs={}, ttlMs={}",
          dto.getSignalId(),
          ctx.getSavedSignalId(),
          ageMs,
          ttlMs);
      return StepResult.fail(rej);
    }

    // Latency guard (optional)
    Long maxLatencyMs =
        dto.getConstraints() != null ? dto.getConstraints().getMaxLatencyMs() : null;
    if (maxLatencyMs != null && maxLatencyMs > 0) {
      if (ageMs > maxLatencyMs) {
        Rejection rej =
            Rejections.latencyExceeded(clock, ageMs, maxLatencyMs, dto.getCreatedAt(), now);
        log.warn(
            "Freshness check rejected: externalId={}, signalId={}, ageMs={}, maxLatencyMs={}",
            dto.getSignalId(),
            ctx.getSavedSignalId(),
            ageMs,
            maxLatencyMs);
        return StepResult.fail(rej);
      }
    }

    log.debug(
        "Freshness check passed: externalId={}, signalId={}, ageMs={}",
        dto.getSignalId(),
        ctx.getSavedSignalId(),
        ageMs);
    return StepResult.pass();
  }
}
*/
