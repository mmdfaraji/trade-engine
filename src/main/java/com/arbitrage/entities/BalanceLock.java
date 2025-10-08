package com.arbitrage.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "balance_locks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "ux_balance_locks_account_currency_signal",
            columnNames = {"exchange_account_id", "currency_id", "signal_id"}),
    indexes =
        @Index(
            name = "ix_balance_locks_account_currency",
            columnList = "exchange_account_id,currency_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BalanceLock extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private ExchangeAccount exchangeAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private BigDecimal amount;
  private String reason;
  private String signalId;
  private Date expiresAt;
}
