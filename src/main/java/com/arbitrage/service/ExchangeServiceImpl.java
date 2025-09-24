package com.arbitrage.service;

import com.arbitrage.entities.Exchange;
import com.arbitrage.respository.ExchangeRepository;
import com.arbitrage.service.api.ExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeServiceImpl implements ExchangeService {
  private final ExchangeRepository exchangeRepository;

  @Override
  public Exchange resolveExchangeByName(String exchangeName) {
    return exchangeRepository
        .findByNameEqualsIgnoreCase(exchangeName)
        .orElseThrow(() -> new IllegalArgumentException("Unknown exchange code: " + exchangeName));
  }
}
