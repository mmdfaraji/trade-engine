package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "signal_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignalEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  private String event;

  @Lob private String payload;
}
