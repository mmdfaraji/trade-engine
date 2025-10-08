package com.arbitrage.dto.balance;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class BucketKeyDto {
  private final Long accountId;
  private final Long currencyId;
}
