package com.arbitrage.service;

import com.arbitrage.dto.market.MarketPairKeyDto;
import com.arbitrage.dto.plan.ExecutionLegPlanDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.entities.PairExchange;
import com.arbitrage.repository.PairExchangeRepository;
import com.arbitrage.service.api.PairExchangeService;
import jakarta.transaction.Transactional;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PairExchangeServiceImpl implements PairExchangeService {

  private final PairExchangeRepository pairExchangeRepository;

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public Map<MarketPairKeyDto, PairExchange> findRulesForPlan(ExecutionPlanDto plan) {
    Set<Long> exIds = new HashSet<>();
    Set<Long> pairIds = new HashSet<>();
    for (ExecutionLegPlanDto l : plan.getLegs()) {
      exIds.add(l.getExchangeId());
      pairIds.add(l.getPairId());
    }
    List<PairExchange> rows =
        pairExchangeRepository.findByExchange_IdInAndPair_IdIn(exIds, pairIds);

    Map<MarketPairKeyDto, PairExchange> map = new HashMap<>();
    for (PairExchange pe : rows) {
      map.put(new MarketPairKeyDto(pe.getExchange().getId(), pe.getPair().getId()), pe);
    }
    return map;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public Map<MarketPairKeyDto, PairExchange> findByKeys(Set<MarketPairKeyDto> keys) {
    if (keys == null || keys.isEmpty()) return Map.of();

    Set<Long> exIds = new HashSet<>();
    Set<Long> pairIds = new HashSet<>();
    for (MarketPairKeyDto k : keys) {
      exIds.add(k.getExchangeId());
      pairIds.add(k.getPairId());
    }
    List<PairExchange> rows =
        pairExchangeRepository.findByExchange_IdInAndPair_IdIn(exIds, pairIds);

    Map<MarketPairKeyDto, PairExchange> map = new HashMap<>();
    for (PairExchange pe : rows) {
      map.put(new MarketPairKeyDto(pe.getExchange().getId(), pe.getPair().getId()), pe);
    }
    return map;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public Optional<PairExchange> findOne(Long exchangeId, Long pairId) {
    return pairExchangeRepository.findByExchange_IdAndPair_Id(exchangeId, pairId);
  }
}
