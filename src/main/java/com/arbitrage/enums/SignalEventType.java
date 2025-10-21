package com.arbitrage.enums;

public enum SignalEventType {
  RECEIVED,

  INTEGRITY_OK,
  INTEGRITY_FAILED,
  FRESHNESS_OK,
  FRESHNESS_FAILED,
  BALANCE_OK,
  BALANCE_FAILED,
  MARKET_OK,
  MARKET_FAILED,
  LIQUIDITY_OK,
  LIQUIDITY_FAILED,
  PNL_OK,
  PNL_FAILED,
  RISK_OK,
  RISK_FAILED,

  ACCEPTED, // all validations passed
  REJECTED, // final rejected (mirror of status change)
  STATUS_CHANGED // generic hook if later needed
}
