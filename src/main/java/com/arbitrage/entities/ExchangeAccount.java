package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "exchange_accounts",
    indexes =
        @Index(
            name = "ix_exchange_accounts_exchange_is_primary",
            columnList = "exchange_id,is_primary"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExchangeAccount extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Exchange exchange;

  private String label;
  private String apiKeyRef;
  private Boolean isPrimary;
}
