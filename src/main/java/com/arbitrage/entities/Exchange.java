package com.arbitrage.entities;

import com.arbitrage.enums.ExchangeStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exchanges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends BaseEntity {

  private String name;

  private String publicApiUrl;
  private String publicWsUrl;

  private String privateApiUrl;
  private String privateWsUrl;

  @Enumerated(EnumType.STRING)
  private ExchangeStatus status;
}
