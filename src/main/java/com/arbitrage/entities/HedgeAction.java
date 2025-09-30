package com.arbitrage.entities;

import com.arbitrage.enums.HedgeStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "hedge_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class HedgeAction extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  private String cause;
  private Long fromOrderId;
  private Long hedgeOrderId;

  private BigDecimal qty;

  @Enumerated(EnumType.STRING)
  private HedgeStatus status;

  @Lob private String resultDetails;
}
