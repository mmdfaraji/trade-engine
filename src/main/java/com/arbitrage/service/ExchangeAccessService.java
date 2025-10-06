package com.arbitrage.service;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.enums.ExchangeStatus;
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

  private final ExchangeRepository exchangeRepo;
  private final ExchangeAccountRepository accountRepo;

  private final ConcurrentMap<String, Exchange> exchangeCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ExchangeAccount> accountCache = new ConcurrentHashMap<>();

  public ExchangeAccessService(
      ExchangeRepository exchangeRepo, ExchangeAccountRepository accountRepo) {
    this.exchangeRepo = exchangeRepo;
    this.accountRepo = accountRepo;
  }

  @Transactional(readOnly = true)
  public Exchange requireExchange(String name) {
    String key = name.toLowerCase(Locale.ROOT);
    Exchange cached = exchangeCache.get(key);
    if (cached != null) return cached;

    Optional<Exchange> opt = exchangeRepo.findByNameIgnoreCase(name);
    Exchange ex = opt.orElseThrow(() -> new IllegalStateException("Exchange not found: " + name));
    if (ex.getStatus() == ExchangeStatus.INACTIVE) {
      throw new IllegalStateException("Exchange is disabled: " + name);
    }
    exchangeCache.put(key, ex);
    return ex;
  }

  @Transactional(readOnly = true)
  public ExchangeAccount requireAccount(String exchangeName, String accountLabel) {
    Exchange ex = requireExchange(exchangeName);
    String key = (exchangeName + ":" + accountLabel).toLowerCase(Locale.ROOT);

    ExchangeAccount cached = accountCache.get(key);
    if (cached != null) return cached;

    Optional<ExchangeAccount> opt = accountRepo.findFirstByExchangeOrderByIdAsc(ex);
    ExchangeAccount acc =
        opt.orElseGet(
            () ->
                accountRepo
                    .findFirstByExchangeOrderByIdAsc(ex)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "No enabled account for exchange " + exchangeName)));

    if (acc.getExchange().getStatus() == ExchangeStatus.INACTIVE) {
      throw new IllegalStateException("ExchangeAccount is disabled: " + accountLabel);
    }
    accountCache.put(key, acc);
    return acc;
  }
}
