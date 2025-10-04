package com.arbitrage.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "execution_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExecutionReport extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  private String finalState;
  private BigDecimal netPositionDelta;
  private BigDecimal pnlRealized;
  private Long latencyMs;
  private BigDecimal slippageBps;
}
