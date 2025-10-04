package com.arbitrage.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "order_fills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderFill extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Order order;

  private Integer fillSeq;
  private BigDecimal filledQty;
  private BigDecimal price;
  private BigDecimal feeAmount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Currency feeCurrency;

  private String tradeId;
  private Date filledAt;
}
