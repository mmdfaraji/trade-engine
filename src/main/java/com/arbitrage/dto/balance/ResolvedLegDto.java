package com.arbitrage.dto.balance;

import com.arbitrage.enums.OrderSide;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResolvedLegDto {
  // Input identity
  private final int index;

  // Exchange / Account / Pair
  private final Long exchangeId;
  private final String exchangeName;
  private final Long exchangeAccountId;
  private final Long pairId;

  // Currencies
  private final Long baseCurrencyId;
  private final Long quoteCurrencyId;
  private final Long spendCurrencyId; // BUY->quote, SELL->base
  private final Long receiveCurrencyId; // BUY->base,  SELL->quote

  // Input attributes
  private final String marketSymbol; // pairs.symbol
  private final OrderSide side;
  private final BigDecimal qty;
  private final BigDecimal price;

  // Computed
  private final BigDecimal requiredSpend; // BUY: qty*price (quote), SELL: qty (base)
}
