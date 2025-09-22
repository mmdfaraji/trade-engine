package com.arbitrage.enums;

import lombok.Getter;

@Getter
public enum EventType {
  RECEIVED,
  STATE_CHANGE,
  NEW,
  SENT,
  PARTIAL,
  FILLED,
  CANCELLED
}
