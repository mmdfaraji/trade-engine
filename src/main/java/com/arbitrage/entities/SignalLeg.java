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
@Table(name = "signal_legs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignalLeg extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  @ManyToOne(fetch = FetchType.LAZY)
  private Pair pair;

  private String side;
  private BigDecimal price;
  private BigDecimal qty;
  private String tif;
  private String desiredRole;
}
