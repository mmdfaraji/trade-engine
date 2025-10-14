package com.arbitrage.service;

import com.arbitrage.dto.OrderInstructionDto;

public interface Trader {

  void submitOrder(OrderInstructionDto orderInstructionDto);
}
