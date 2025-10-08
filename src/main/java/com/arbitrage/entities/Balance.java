package com.arbitrage.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "balances",
    uniqueConstraints =
        @UniqueConstraint(
            name = "ux_balances_account_currency",
            columnNames = {"exchange_account_id", "currency_id"}),
    indexes = {
      @Index(name = "ix_balances_account", columnList = "exchange_account_id"),
      @Index(name = "ix_balances_currency", columnList = "currency_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Balance extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private ExchangeAccount exchangeAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private BigDecimal available;
  private BigDecimal reserved;
}
