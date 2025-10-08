package com.arbitrage.service;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.respository.ExchangeAccountRepository;
import com.arbitrage.respository.ExchangeRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    String key = name.toLowerCase(Locale.ROOT);
    Exchange cached = exchanges.get(key);
    if (cached != null) return cached;

    Optional<Exchange> found = exchangeRepository.findByNameIgnoreCase(name);
    Exchange ex = found.orElseThrow(() -> new IllegalStateException("Exchange not found: " + name));
    exchanges.put(key, ex);
    return ex;
  }

  @Transactional(readOnly = true)
  public ExchangeAccount requireAccount(String exchangeName, String accountLabel) {
    Exchange ex = requireExchange(exchangeName);
    String key = (exchangeName + ":" + accountLabel).toLowerCase(Locale.ROOT);
    ExchangeAccount cached = accounts.get(key);
    if (cached != null) return cached;

    Optional<ExchangeAccount> byLabel =
        exchangeAccountRepository.findFirstByExchangeAndLabelIgnoreCase(ex, accountLabel);

    ExchangeAccount acc =
        byLabel.orElseGet(
            () ->
                exchangeAccountRepository
                    .findFirstByExchange(ex)
                    .orElseThrow(
                        () ->
                            new IllegalStateException("No account for exchange " + exchangeName)));

    accounts.put(key, acc);
    return acc;
  }
}
