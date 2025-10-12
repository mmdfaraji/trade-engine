package com.arbitrage.dto.plan;

import com.arbitrage.enums.OrderSide;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Execution-ready leg plan (phase-2 output). It contains resolved identifiers and computed amounts.
 */
@Value
@Builder
public class ExecutionLegPlanDto {
  int index;

  Long exchangeId;
  Long exchangeAccountId;
  Long pairId;

  Long baseCurrencyId;
  Long quoteCurrencyId;
  Long spendCurrencyId; // BUY->quote, SELL->base
  Long receiveCurrencyId; // BUY->base,  SELL->quote

  String exchangeName;
  String marketSymbol; // pairs.symbol

  OrderSide side;
  BigDecimal reqQty; // requested base qty
  BigDecimal price;

  BigDecimal requiredSpend; // BUY: qty*price (quote), SELL: qty (base)
  BigDecimal execQty; // phase-2: equals reqQty if reserved; else 0
}
