package com.arbitrage.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

  private String symbol;
  private String side;
  private BigDecimal qty;
  private BigDecimal price;
  private String tif;
}
