package com.arbitrage.enums;

import lombok.Getter;

@Getter
public enum ExecutionFinalState {
  FILLED,
  PARTIAL_HEDGED,
  CANCELLED
}
