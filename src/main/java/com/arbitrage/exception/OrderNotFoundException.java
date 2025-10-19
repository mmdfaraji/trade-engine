package com.arbitrage.exception;

public class OrderNotFoundException extends RuntimeException {

  private String orderId;

  public OrderNotFoundException(String message) {
    super(message);
  }

  public OrderNotFoundException(String orderId, String message) {
    super(message);
    this.orderId = orderId;
  }

  public OrderNotFoundException(String orderId, String message, Throwable cause) {
    super(message, cause);
    this.orderId = orderId;
  }

  public String getOrderId() {
    return orderId;
  }
}
