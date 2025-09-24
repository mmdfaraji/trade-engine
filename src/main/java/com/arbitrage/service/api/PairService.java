package com.arbitrage.service.api;

import com.arbitrage.entities.Pair;

public interface PairService {
  Pair resolvePairBySymbol(String symbol);
}
