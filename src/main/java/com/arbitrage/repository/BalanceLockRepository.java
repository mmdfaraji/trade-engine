package com.arbitrage.repository;

import com.arbitrage.entities.BalanceLock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceLockRepository extends JpaRepository<BalanceLock, Long> {
  Optional<BalanceLock> findByExchangeAccount_IdAndCurrency_IdAndSignal_Id(
      Long accountId, Long currencyId, String signalId);
}
