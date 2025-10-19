package com.arbitrage.model;

import com.arbitrage.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.Objects;

public class ExchangeOrderStatus {

  private final OrderStatus status;
  private final BigDecimal filledQuantity;
  private final BigDecimal averagePrice;
  private final BigDecimal executedNotional;

  public ExchangeOrderStatus(
      OrderStatus status,
      BigDecimal filledQuantity,
      BigDecimal averagePrice,
      BigDecimal executedNotional) {
    this.status = Objects.requireNonNullElse(status, OrderStatus.SENT);
    this.filledQuantity = filledQuantity;
    this.averagePrice = averagePrice;
    this.executedNotional = executedNotional;
  }

  public OrderStatus status() {
    return status;
  }

  public BigDecimal filledQuantity() {
    return filledQuantity;
  }

  public BigDecimal averagePrice() {
    return averagePrice;
  }

  public BigDecimal executedNotional() {
    return executedNotional;
  }

  public static ExchangeOrderStatus of(OrderStatus status) {
    return new ExchangeOrderStatus(status, null, null, null);
  }

  public ExchangeOrderStatus withFilledQuantity(BigDecimal filledQuantity) {
    return new ExchangeOrderStatus(status, filledQuantity, averagePrice, executedNotional);
  }

  public ExchangeOrderStatus withAveragePrice(BigDecimal averagePrice) {
    return new ExchangeOrderStatus(status, filledQuantity, averagePrice, executedNotional);
  }

  public ExchangeOrderStatus withExecutedNotional(BigDecimal executedNotional) {
    return new ExchangeOrderStatus(status, filledQuantity, averagePrice, executedNotional);
  }
}
