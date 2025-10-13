package com.arbitrage.service.market;

import com.arbitrage.dto.OrderInstructionDto;
import com.arbitrage.service.api.Trader;

public class TraderService implements Trader {

  @Override
  public void submitOrder(OrderInstructionDto orderInstructionDto) {
    // ToDo: create OrderRequest model from orderInstructionDto

    // ToDo: send orderRequest to exchange from orderInstructionDto object

    // ToDo: save order object in database

    // ToDo: update BalanceLock table and Balance table
  }
}
