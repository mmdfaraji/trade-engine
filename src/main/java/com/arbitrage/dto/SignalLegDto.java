package com.arbitrage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalLegDto {

  // Incoming: "Exchange" (e.g. "ramzinex")
  @JsonProperty("Exchange")
  private String exchange;

  // Incoming: "market" (e.g. "USDT/IRR")
  @JsonProperty("market")
  private String market;

  // Incoming: "Side" (e.g. "buy"|"sell")
  @JsonProperty("Side")
  private String side;

  @JsonProperty("Price")
  private BigDecimal price;

  @JsonProperty("Qty")
  private BigDecimal qty;

  @JsonProperty("time_in_force")
  private String timeInForce;

  private String desiredRole;
}
