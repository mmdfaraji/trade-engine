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
@Table(name = "pairs")
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
