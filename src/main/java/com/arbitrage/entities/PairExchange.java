package com.arbitrage.entities;

import com.arbitrage.enums.PairExchangeStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "pair_exchanges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PairExchange extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  @ManyToOne(fetch = FetchType.LAZY)
  private Pair pair;

  private String exchangeSymbol;
  private BigDecimal tickSize;
  private BigDecimal stepSize;
  private BigDecimal minNotional;
  private BigDecimal maxOrderSize;
  private BigDecimal packSize;
  private BigDecimal makerFeeBps;
  private BigDecimal takerFeeBps;

  @Enumerated(EnumType.STRING)
  private PairExchangeStatus status;
}
