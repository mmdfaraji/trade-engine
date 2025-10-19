package com.arbitrage.respository;

import com.arbitrage.entities.BalanceLock;
import com.arbitrage.entities.Currency;
import com.arbitrage.entities.ExchangeAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceLockRepository extends JpaRepository<BalanceLock, Long> {

  Optional<BalanceLock> findByExchangeAccountAndCurrencyAndReasonAndSignalId(
      ExchangeAccount exchangeAccount, Currency currency, String reason, String signalId);
}
