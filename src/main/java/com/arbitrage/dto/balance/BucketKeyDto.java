package com.arbitrage.dto.balance;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Reservation bucket key: (exchangeAccountId, spendCurrencyId). */
@Getter
@EqualsAndHashCode
public class BucketKeyDto {
  private final Long accountId;
  private final Long currencyId;

  public BucketKeyDto(Long accountId, Long currencyId) {
    this.accountId = accountId;
    this.currencyId = currencyId;
  }

  @Override
  public String toString() {
    return "acct=" + accountId + ",cur=" + currencyId;
  }
}
