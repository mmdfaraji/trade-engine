package com.arbitrage.service;

import com.arbitrage.entities.Pair;
import com.arbitrage.repository.PairRepository;
import com.arbitrage.service.api.PairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PairServiceImpl implements PairService {
  private final PairRepository pairRepository;

  @Override
  public Pair resolvePairBySymbol(String symbol) {
    return pairRepository
        .findBySymbolEqualsIgnoreCase(symbol)
        .orElseThrow(() -> new IllegalArgumentException("Unknown pair/market: " + symbol));
  }
}
