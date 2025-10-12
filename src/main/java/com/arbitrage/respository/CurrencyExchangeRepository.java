package com.arbitrage.respository;

import com.arbitrage.entities.CurrencyExchange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyExchangeRepository extends JpaRepository<CurrencyExchange, Long> {

  List<CurrencyExchange> findByExchange_Name(String exchangeName);

  CurrencyExchange findByExchange_NameAndCurrency_Name(String exchangeName, String currencyName);
}
