package com.arbitrage.service.api;

import com.arbitrage.entities.Exchange;

public interface ExchangeService {
  Exchange resolveExchangeByName(String exchangeName);
}
