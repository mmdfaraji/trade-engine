package com.arbitrage.repository;

import com.arbitrage.entities.Pair;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairRepository extends JpaRepository<Pair, Long> {
  Optional<Pair> findBySymbolEqualsIgnoreCase(String symbol);
}
