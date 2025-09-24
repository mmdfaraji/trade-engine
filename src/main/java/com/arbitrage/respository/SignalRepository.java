package com.arbitrage.respository;

import com.arbitrage.entities.Signal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, Long> {
  Optional<Signal> findByExternalId(String externalId);
}
