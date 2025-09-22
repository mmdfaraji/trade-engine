package com.arbitrage.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
  NEW,
  SENT,
  PARTIAL,
  FILLED,
  CANCELLED
}
