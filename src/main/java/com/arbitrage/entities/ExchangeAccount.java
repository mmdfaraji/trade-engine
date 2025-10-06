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
public class ExchangeAccount extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  private String label;
  private String apiKey;
  private String secretKey;
  private Boolean isPrimary;
}
