package com.arbitrage.dto.plan;

import com.arbitrage.enums.OrderSide;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExecutionLegPlanDto {
  // Input resolution & identity
  int index; // input leg index
  Long exchangeId; // resolved exchange id
  Long exchangeAccountId; // chosen primary account id for exchange
  Long pairId; // resolved pair id

  // Currencies
  Long baseCurrencyId; // from pair
  Long quoteCurrencyId; // from pair
  Long spendCurrencyId; // BUY->quote, SELL->base
  Long receiveCurrencyId; // BUY->base,  SELL->quote

  // For logs
  String exchangeName; // for logs
  String marketSymbol; // pairs.symbol (internal standard)

  // Input numbers
  OrderSide side; // BUY | SELL
  BigDecimal reqQty; // requested qty from DTO
  BigDecimal price; // price from DTO

  // Computed
  BigDecimal requiredSpend; // BUY: qty*price (quote), SELL: qty (base)
  BigDecimal execQty; // == reqQty in phase-2 (all-or-nothing policy)
}
