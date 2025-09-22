package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "pairs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Pair extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency baseCurrency;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency quoteCurrency;

  private String symbol;
}
