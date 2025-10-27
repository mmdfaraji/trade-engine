package com.arbitrage.dto.market;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class MarketPairKeyDto {
  private final Long exchangeId;
  private final Long pairId;

  public MarketPairKeyDto(Long exchangeId, Long pairId) {
    this.exchangeId = exchangeId;
    this.pairId = pairId;
  }

  @Override
  public String toString() {
    return exchangeId + ":" + pairId;
  }
}
