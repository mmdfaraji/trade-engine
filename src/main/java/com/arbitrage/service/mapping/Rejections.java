package com.arbitrage.service.mapping;

import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Factory helpers for common rejection shapes. */
public final class Rejections {

  private Rejections() {}

  public static Rejection referenceNotFound(
      Clock clock, String validator, String refType, String refValue, Integer legIndex) {
    return Rejection.builder()
        .code(RejectCode.REFERENCE_NOT_FOUND)
        .message("Reference not found")
        .phase(ValidationPhase.PHASE0_PERSIST)
        .validator(validator)
        .legIndex(legIndex)
        .occurredAt(clock.instant())
        .detail("referenceType", refType)
        .detail("referenceValue", refValue)
        .build();
  }

  public static Rejection stale(
      Clock clock, long ageMs, long ttlMs, Instant createdAt, Instant now) {
    return Rejection.builder()
        .code(RejectCode.STALE)
        .message("Signal is stale (age > ttl)")
        .phase(ValidationPhase.PHASE1_FRESHNESS)
        .validator("FreshnessValidator")
        .occurredAt(clock.instant())
        .detail("ageMs", ageMs)
        .detail("ttlMs", ttlMs)
        .detail("createdAt", createdAt)
        .detail("now", now)
        .build();
  }

  public static Rejection insufficientFunds(
      Clock clock,
      int legIndex,
      String exchange,
      String market,
      String asset,
      String side,
      Object required,
      Object available) {
    return Rejection.builder()
        .code(RejectCode.INSUFFICIENT_FUNDS)
        .message("Insufficient funds for leg")
        .phase(ValidationPhase.PHASE2_BALANCE)
        .validator("BalanceService")
        .legIndex(legIndex)
        .occurredAt(clock.instant())
        .detail("exchange", exchange)
        .detail("market", market)
        .detail("asset", asset)
        .detail("side", side)
        .detail("required", required)
        .detail("available", available)
        .build();
  }

  public static Rejection marketRuleViolation(
      Clock clock,
      int legIndex,
      String exchange,
      String market,
      String field,
      Object value,
      Map<String, Object> rule) {
    return Rejection.builder()
        .code(RejectCode.MARKET_RULE_VIOLATION)
        .message("Market rule violated")
        .phase(ValidationPhase.PHASE3_MARKET)
        .validator("RoundingService")
        .legIndex(legIndex)
        .occurredAt(clock.instant())
        .detail("exchange", exchange)
        .detail("market", market)
        .detail("field", field)
        .detail("value", value)
        .detail("rule", rule)
        .build();
  }

  public static Rejection insufficientLiquidity(
      Clock clock,
      int legIndex,
      String exchange,
      String market,
      Object qtyExec,
      Object bookAvailable,
      Object maxSlippageBps,
      Object worstFillPrice) {
    return Rejection.builder()
        .code(RejectCode.INSUFFICIENT_LIQUIDITY)
        .message("Insufficient order book liquidity")
        .phase(ValidationPhase.PHASE4_LIQUIDITY)
        .validator("LiquidityService")
        .legIndex(legIndex)
        .occurredAt(clock.instant())
        .detail("qtyExec", qtyExec)
        .detail("bookAvailable", bookAvailable)
        .detail("maxSlippageBps", maxSlippageBps)
        .detail("worstFillPrice", worstFillPrice)
        .build();
  }

  public static Rejection pnlTooLow(
      Clock clock, Object expectedNet, Object minExpectedPnl, Object fees, Object slippageCost) {
    return Rejection.builder()
        .code(RejectCode.PNL_TOO_LOW)
        .message("Expected net PnL below threshold")
        .phase(ValidationPhase.PHASE5_PNL)
        .validator("FeesService")
        .occurredAt(clock.instant())
        .detail("expectedNet", expectedNet)
        .detail("minExpectedPnl", minExpectedPnl)
        .detail("fees", fees)
        .detail("slippageCost", slippageCost)
        .build();
  }

  public static Rejection riskLimit(
      Clock clock, String limitName, Object currentExposure, Object newExposure, Object limit) {
    return Rejection.builder()
        .code(RejectCode.RISK_LIMIT)
        .message("Risk limit exceeded")
        .phase(ValidationPhase.PHASE6_RISK)
        .validator("RiskService")
        .occurredAt(clock.instant())
        .detail("limitName", limitName)
        .detail("currentExposure", currentExposure)
        .detail("newExposure", newExposure)
        .detail("limit", limit)
        .build();
  }

  public static Rejection transientUpstream(
      Clock clock, String dependency, String reason, Long retryAfterMs) {
    return Rejection.builder()
        .code(RejectCode.TRANSIENT_UPSTREAM)
        .message("Transient upstream dependency issue")
        .phase(ValidationPhase.PHASE3_MARKET) // or appropriate phase
        .validator(dependency)
        .occurredAt(clock.instant())
        .detail("reason", reason)
        .detail("retryAfterMs", retryAfterMs)
        .build();
  }

  public static Rejection internalError(
      Clock clock, String validator, String error, String exceptionClass) {
    return Rejection.builder()
        .code(RejectCode.INTERNAL_ERROR)
        .message("Internal error")
        .phase(ValidationPhase.PHASE0_PERSIST) // or the actual phase
        .validator(validator)
        .occurredAt(clock.instant())
        .detail("error", error)
        .detail("exceptionClass", exceptionClass)
        .build();
  }


  public static Rejection latencyExceeded(Clock clock, long ageMs, long maxLatencyMs, Instant createdAt, Instant now) {
    return Rejection.builder()
            .code(RejectCode.STALE)
            .message("Latency is above the configured maximum")
            .phase(ValidationPhase.PHASE1_FRESHNESS)
            .validator("FreshnessValidator")
            .occurredAt(clock.instant())
            .detail("ageMs", ageMs)
            .detail("maxLatencyMs", maxLatencyMs)
            .detail("createdAt", createdAt)
            .detail("now", now)
            .build();
  }

}
