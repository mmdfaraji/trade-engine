package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "exchange_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExchangeAccount extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  private String label;
  private String apiKeyRef;
  private Boolean isPrimary;
}
