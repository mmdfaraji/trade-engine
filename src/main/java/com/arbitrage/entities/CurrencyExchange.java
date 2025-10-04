package com.arbitrage.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "currency_exchanges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CurrencyExchange extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private String exchangeSymbol;
  private Integer scaleOverride;
}
