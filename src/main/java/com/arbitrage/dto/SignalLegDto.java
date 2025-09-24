package com.arbitrage.dto;

import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalLegDto {
  private String exchangeName; // lookup key for Exchange
  private String pairSymbol; // lookup key for Pair
  private String side;
  private BigDecimal price;
  private BigDecimal qty;
  private String tif;
  private String desiredRole;
}
