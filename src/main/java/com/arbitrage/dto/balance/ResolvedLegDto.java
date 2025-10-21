package com.arbitrage.dto.balance;

import com.arbitrage.enums.OrderSide;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * Internal resolution of a leg before building ExecutionLegPlanDto. It binds inbound leg to
 * resolved exchange/pair/account/currencies.
 */
@Getter
@Builder
public class ResolvedLegDto {
  private final int index;
  private final Long exchangeId;
  private final String exchangeName;
  private final Long exchangeAccountId;
  private final Long pairId;
  private final Long baseCurrencyId;
  private final Long quoteCurrencyId;
  private final Long spendCurrencyId; // BUY -> quote, SELL -> base
  private final Long receiveCurrencyId; // BUY -> base,  SELL -> quote
  private final String marketSymbol;
  private final OrderSide side;
  private final BigDecimal qty; // base quantity
  private final BigDecimal price;
  private final BigDecimal requiredSpend; // BUY: qty*price, SELL: qty
}
