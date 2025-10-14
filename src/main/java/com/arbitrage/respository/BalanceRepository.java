package com.arbitrage.respository;

import com.arbitrage.entities.Balance;
import com.arbitrage.entities.Currency;
import com.arbitrage.entities.ExchangeAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {

  Optional<Balance> findByExchangeAccountAndCurrency(
      ExchangeAccount exchangeAccount, Currency currency);
}
