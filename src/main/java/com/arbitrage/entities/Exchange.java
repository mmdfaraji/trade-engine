package com.arbitrage.entities;

import com.arbitrage.enums.ExchangeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "exchanges",
    uniqueConstraints = @UniqueConstraint(name = "ux_exchanges_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends LongIdEntity {

  private String name;

  private String publicApiUrl;
  private String publicWsUrl;

  private String privateApiUrl;
  private String privateWsUrl;

  @Enumerated(EnumType.STRING)
  private ExchangeStatus status;
}
