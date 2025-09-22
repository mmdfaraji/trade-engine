package com.arbitrage.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "signal_legs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignalLeg extends BaseEntity {

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
