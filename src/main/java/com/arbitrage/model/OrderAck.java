package com.arbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAck {

  private String clientOrderId;
  private String exchangeOrderId;
  private String status;
}
