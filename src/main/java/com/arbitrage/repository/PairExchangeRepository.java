package com.arbitrage.repository;

import com.arbitrage.entities.PairExchange;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairExchangeRepository extends JpaRepository<PairExchange, Long> {
  Optional<PairExchange> findByExchange_IdAndPair_Id(Long exchangeId, Long pairId);

  List<PairExchange> findByExchange_IdInAndPair_IdIn(
      Collection<Long> exchangeIds, Collection<Long> pairIds);
}
