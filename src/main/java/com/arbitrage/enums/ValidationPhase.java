package com.arbitrage.enums;

public enum ValidationPhase {
  PHASE0_INTEGRITY,
  PHASE0_PERSIST, // initial persist/idempotency/normalization
  PHASE1_FRESHNESS, // ttl / latency validation
  PHASE2_BALANCE, // balances & reservation
  PHASE3_MARKET, // market rules (tick/step/pack/minNotional/ceiling)
  PHASE4_LIQUIDITY, // order book depth / slippage
  PHASE5_PNL, // fees & expected pnl
  PHASE6_RISK // risk/exposure limits
}
