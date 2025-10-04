package com.arbitrage.respository;

import com.arbitrage.entities.Signal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {
  Optional<Signal> findByExternalId(String externalId);
}
