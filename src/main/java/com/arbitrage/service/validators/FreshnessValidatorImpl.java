package com.arbitrage.service.validators;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.service.mapping.Rejections;
import com.arbitrage.service.validators.api.FreshnessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessValidatorImpl implements FreshnessValidator {

    private final Clock clock;

    @Override
    public StepResult validate(SignalContext ctx) {
        // --- Use producer timestamp and TTL/Latency from DTO ---
        SignalMessageDto dto = ctx.getDto();
        Instant now = ctx.getNow() != null ? ctx.getNow() : clock.instant();

        // If createdAt is missing, we skip freshness (policy choice: pass + warn)
        if (dto.getCreatedAt() == null) {
            // Comments are in English as requested.
            log.warn("FreshnessValidator: missing createdAt; externalId={}, signalId={}",
                    dto.getSignalId(), ctx.getSavedSignalId());
            return StepResult.pass();
        }

        long ageMs = Duration.between(dto.getCreatedAt(), now).toMillis();

        // 1) TTL check
        if (dto.getTtlMs() != null && dto.getTtlMs() > 0) {
            if (ageMs > dto.getTtlMs()) {
                Rejection rej = Rejections.stale(clock, ageMs, dto.getTtlMs(), dto.getCreatedAt(), now);
                // Log with structured details
                log.warn("FreshnessValidator: TTL exceeded -> externalId={}, signalId={}, ageMs={}, ttlMs={}",
                        dto.getSignalId(), ctx.getSavedSignalId(), ageMs, dto.getTtlMs());
                return StepResult.fail(rej);
            }
        } else {
            // no ttl provided -> pass but info log to help tuning
            log.debug("FreshnessValidator: no TTL provided; externalId={}, ageMs={}",
                    dto.getSignalId(), ageMs);
        }

        // 2) Latency guard from constraints (optional)
        Long maxLatencyMs = dto.getConstraints() != null ? dto.getConstraints().getMaxLatencyMs() : null;
        if (maxLatencyMs != null && maxLatencyMs > 0) {
            if (ageMs > maxLatencyMs) {
                Rejection rej = Rejections.latencyExceeded(clock, ageMs, maxLatencyMs, dto.getCreatedAt(), now);
                log.warn("FreshnessValidator: Latency exceeded -> externalId={}, signalId={}, ageMs={}, maxLatencyMs={}",
                        dto.getSignalId(), ctx.getSavedSignalId(), ageMs, maxLatencyMs);
                return StepResult.fail(rej);
            }
        }

        // All good
        log.debug("FreshnessValidator: passed; externalId={}, signalId={}, ageMs={}",
                dto.getSignalId(), ctx.getSavedSignalId(), ageMs);
        return StepResult.pass();
    }
}
