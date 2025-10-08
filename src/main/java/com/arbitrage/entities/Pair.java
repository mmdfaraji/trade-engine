package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "pairs",
    uniqueConstraints = @UniqueConstraint(name = "ux_pairs_symbol", columnNames = "symbol"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Pair extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency baseCurrency;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency quoteCurrency;

  private String symbol;
}
