package com.arbitrage.respository;

import com.arbitrage.entities.Exchange;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Long> {

  Optional<Exchange> findByNameIgnoreCase(String name);
}
