package com.arbitrage.service.api;

import com.arbitrage.dto.market.MarketPairKeyDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.entities.PairExchange;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface PairExchangeService {

  /** Batch load rules for all legs inside the plan and index by (exchangeId,pairId). */
  Map<MarketPairKeyDto, PairExchange> findRulesForPlan(ExecutionPlanDto plan);

  /** Batch load rules for an arbitrary set of keys. */
  Map<MarketPairKeyDto, PairExchange> findByKeys(Set<MarketPairKeyDto> keys);

  /** Single fetch, useful for ad-hoc queries. */
  Optional<PairExchange> findOne(Long exchangeId, Long pairId);
}
