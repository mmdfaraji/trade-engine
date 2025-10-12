package com.arbitrage.respository;

import com.arbitrage.entities.PairExchange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PairExchangeRepository extends JpaRepository<PairExchange, Long> {

  List<PairExchange> findByExchange_Name(String exchangeName);
}
