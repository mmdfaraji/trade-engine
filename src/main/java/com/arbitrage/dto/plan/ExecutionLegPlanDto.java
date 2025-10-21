package com.arbitrage.dto.plan;

import com.arbitrage.enums.OrderSide;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * Execution-ready leg plan (phase-2 output). It contains resolved identifiers and computed amounts.
 */
@Getter
@Builder
public class ExecutionLegPlanDto {
  private final int index;
  private final Long exchangeId;
  private final Long exchangeAccountId;
  private final Long pairId;
  private final Long baseCurrencyId;
  private final Long quoteCurrencyId;
  private final Long spendCurrencyId;
  private final Long receiveCurrencyId;

  private final String exchangeName;
  private final String marketSymbol;

  private final OrderSide side;
  private final BigDecimal reqQty; // requested base qty
  private final BigDecimal price;
  private final BigDecimal requiredSpend; // BUY: reqQty*price ; SELL: reqQty
  private final BigDecimal
      execQty; // here equals reqQty (no reservation in balance validation phase)
}
