package com.arbitrage.dal;

import com.arbitrage.entities.Currency;
import com.arbitrage.respository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class CurrencyService {

  private final CurrencyRepository currencyRepository;

  public Currency getByName(String name) {
    return currencyRepository
        .findByName(name)
        .orElseThrow(() -> new RuntimeException("Currency not found. name: " + name));
  }
}
