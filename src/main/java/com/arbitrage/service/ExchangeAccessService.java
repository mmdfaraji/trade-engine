package com.arbitrage.service;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.respository.ExchangeAccountRepository;
import com.arbitrage.respository.ExchangeRepository;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExchangeAccessService {

  private final ExchangeRepository exchangeRepository;
  private final ExchangeAccountRepository exchangeAccountRepository;

  private final ConcurrentMap<String, Exchange> exchanges = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ExchangeAccount> accounts = new ConcurrentHashMap<>();

  public ExchangeAccessService(
      ExchangeRepository exchangeRepository, ExchangeAccountRepository exchangeAccountRepository) {
    this.exchangeRepository = exchangeRepository;
    this.exchangeAccountRepository = exchangeAccountRepository;
  }

  @Transactional(readOnly = true)
  public Exchange requireExchange(String name) {
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("Exchange name must not be blank");
    }
    String key = name.toLowerCase(Locale.ROOT);
    return exchanges.computeIfAbsent(
        key,
        k ->
            exchangeRepository
                .findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalStateException("Exchange not found: " + name)));
  }

  @Transactional(readOnly = true)
  public ExchangeAccount requireAccount(String exchangeName, String accountLabel) {
    if (!StringUtils.hasText(accountLabel)) {
      throw new IllegalArgumentException("Account label must not be blank");
    }
    Exchange exchange = requireExchange(exchangeName);
    String key = (exchangeName + ":" + accountLabel).toLowerCase(Locale.ROOT);
    return accounts.computeIfAbsent(
        key,
        k ->
            exchangeAccountRepository
                .findFirstByExchangeAndLabelIgnoreCase(exchange, accountLabel)
                .orElseGet(
                    () ->
                        exchangeAccountRepository
                            .findFirstByExchange(exchange)
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "No account for exchange " + exchangeName))));
  }
}
