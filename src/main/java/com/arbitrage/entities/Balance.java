package com.arbitrage.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "balances")
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
