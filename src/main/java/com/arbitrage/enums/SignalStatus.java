package com.arbitrage.enums;

import lombok.Getter;

@Getter
public enum SignalStatus {
  RECEIVED,
  VALIDATED,
  EXECUTING,
  FILLED,
  PARTIAL,
  CANCELLED,
  REJECTED
}
