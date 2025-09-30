package com.arbitrage.entities;

import com.arbitrage.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends UUIDEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  @ManyToOne(fetch = FetchType.LAZY)
  private ExchangeAccount exchangeAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  private Pair pair;

  private String side;
  private String type;
  private String tif;

  private String clientOrderId;
  private String exchangeOrderId;

  private BigDecimal price;
  private BigDecimal qty;
  private BigDecimal qtyExec;
  private BigDecimal notional;

  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  private BigDecimal filledQty;
  private BigDecimal avgPrice;

  private Date sentAt;
  private Date closedAt;
}
