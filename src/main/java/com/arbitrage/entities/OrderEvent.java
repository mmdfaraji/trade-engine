package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "order_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderEvent extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Order order;

  private String event;

  @Lob private String payload;
}
