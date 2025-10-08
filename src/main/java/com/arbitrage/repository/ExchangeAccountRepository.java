package com.arbitrage.repository;

import com.arbitrage.entities.ExchangeAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccount, Long> {
  Optional<ExchangeAccount> findFirstByExchange_IdAndIsPrimaryTrue(Long exchangeId);
}
