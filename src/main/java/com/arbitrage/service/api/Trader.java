package com.arbitrage.service.api;

import com.arbitrage.dto.OrderInstructionDto;

public interface Trader {

  void submitOrder(OrderInstructionDto orderInstructionDto);
}
