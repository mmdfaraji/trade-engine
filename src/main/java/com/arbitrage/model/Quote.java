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
public class Quote {

  private String symbol;
  private BigDecimal bid;
  private BigDecimal ask;
  private long ts;
}
