package com.arbitrage.dto;

import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.TimeInForce;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One order leg from inbound JSON. We keep enums for side/tif as requested. To be robust with
 * lowercase inputs, a custom String setter is provided for 'side'.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderInstructionDto {

  private String exchangeName; // e.g., "nobitex"
  private String pairName; // e.g., "ETH-USDT" (internal pairs.symbol)

  // Use enum for side; provide a String setter to accept "buy"/"sell".
  private OrderSide side;

  // Numeric objects from inbound JSON
  private DecimalValueDto price;
  private DecimalValueDto baseAmount;
  private DecimalValueDto quoteAmount;

  // Optional; default will be applied later if null (usually IOC)
  private TimeInForce timeInForce;

  // Accept "buy"/"sell" string safely and convert to enum
  @JsonProperty("side")
  public void setSideRaw(String raw) {
    if (raw == null) {
      this.side = OrderSide.BUY;
      return;
    }
    String u = raw.trim().toUpperCase(Locale.ROOT);
    this.side = "SELL".equals(u) ? OrderSide.SELL : OrderSide.BUY;
  }

  // Convenience getters (not serialized) for business logic
  @JsonIgnore
  public BigDecimal getPriceAsBigDecimal() {
    return price != null ? price.toBigDecimal() : BigDecimal.ZERO;
  }

  @JsonIgnore
  public BigDecimal getBaseAmountAsBigDecimal() {
    return baseAmount != null ? baseAmount.toBigDecimal() : BigDecimal.ZERO;
  }

  @JsonIgnore
  public BigDecimal getQuoteAmountAsBigDecimal() {
    return quoteAmount != null ? quoteAmount.toBigDecimal() : BigDecimal.ZERO;
  }

  @JsonIgnore
  public TimeInForce getTimeInForceOrDefault(TimeInForce def) {
    return timeInForce != null ? timeInForce : def;
  }
}
