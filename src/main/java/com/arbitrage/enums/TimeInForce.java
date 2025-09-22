package com.arbitrage.enums;

import lombok.Getter;

@Getter
public enum TimeInForce {
  IOC, // Immediate or Cancel
  FOK, // Fill or Kill
  GTC, // Good Till Cancel
  DAY // End of trading day
}
