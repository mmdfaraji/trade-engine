package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "currency_exchanges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CurrencyExchange extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency currency;

  private String exchangeSymbol;
  private Integer scaleOverride;
}
