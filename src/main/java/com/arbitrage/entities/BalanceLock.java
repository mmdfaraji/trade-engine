package com.arbitrage.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "balance_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BalanceLock extends UUIDEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private ExchangeAccount exchangeAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private BigDecimal amount;
  private String reason;
  private String signalId;
  private Date expiresAt;
}
