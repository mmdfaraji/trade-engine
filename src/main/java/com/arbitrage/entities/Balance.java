package com.arbitrage.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Balance extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private ExchangeAccount exchangeAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private BigDecimal available;
  private BigDecimal reserved;
}
