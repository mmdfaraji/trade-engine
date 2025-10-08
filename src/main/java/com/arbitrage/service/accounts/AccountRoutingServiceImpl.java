package com.arbitrage.service.accounts;

import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.repository.ExchangeAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountRoutingServiceImpl implements AccountRoutingService {

  private final ExchangeAccountRepository exchangeAccountRepository;

  @Override
  public Long getPrimaryAccountId(Long exchangeId) {
    return exchangeAccountRepository
        .findFirstByExchange_IdAndIsPrimaryTrue(exchangeId)
        .map(ExchangeAccount::getId)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No primary account found for exchangeId=" + exchangeId));
  }
}
