package com.arbitrage.respository;

import com.arbitrage.entities.Pair;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PairRepository extends JpaRepository<Pair, Long> {

  Optional<Pair> findBySymbolIgnoreCase(String symbol);
}
