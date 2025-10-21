package com.arbitrage.enums;

public enum RejectCode {
  STALE, // ttl exceeded / latency guard failed
  INSUFFICIENT_FUNDS, // not enough balance to execute the leg/plan
  MARKET_RULE_VIOLATION, // tick/step/pack/minNotional/ceiling violations
  MIN_NOTIONAL, // explicit min notional violation (optional specialization)
  SIZE_CEILING, // explicit size ceiling violation (optional specialization)
  INSUFFICIENT_LIQUIDITY, // order book depth cannot fill qty within slippage window
  PNL_TOO_LOW, // expected net pnl < minimal threshold
  RISK_LIMIT, // portfolio / per-exchange caps / circuit breaker
  TRANSIENT_UPSTREAM, // temporary dependency issue (market data / fees / etc.)
  INTERNAL_ERROR, // unexpected internal error
  REFERENCE_NOT_FOUND,
  INVALID_INPUT,
  // more precise (optional but recommended)
  INTEGRITY_MISSING_META,
  INTEGRITY_MISSING_FIELD,
  INTEGRITY_INVALID_LEG_COUNT,
  INTEGRITY_INVALID_VALUE,
  REFERENCE_EXCHANGE_NOT_FOUND,
  REFERENCE_PAIR_NOT_FOUND,
  REFERENCE_ACCOUNT_NOT_FOUND
}
