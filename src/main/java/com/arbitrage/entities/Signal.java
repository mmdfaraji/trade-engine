package com.arbitrage.entities;

import com.arbitrage.enums.SignalStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "signals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Signal extends UUIDEntity {

  @Column(name = "external_id")
  private String externalId;

  private Long ttlMs;

  @Enumerated(EnumType.STRING)
  private SignalStatus status;

  private String source;

  @Lob private String constraints;

  private BigDecimal expectedPnl;

  @Column(name = "producer_created_at")
  private Instant producerCreatedAt;
}
