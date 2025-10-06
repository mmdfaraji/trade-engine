package com.arbitrage.respository;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccount, Long> {

  Optional<ExchangeAccount> findFirstByExchangeAndLabelIgnoreCase(Exchange exchange, String label);

  Optional<ExchangeAccount> findFirstByExchangeOrderByIdAsc(Exchange exchange);
}
