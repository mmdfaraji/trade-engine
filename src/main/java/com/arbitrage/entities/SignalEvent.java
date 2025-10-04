package com.arbitrage.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "signal_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignalEvent extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  private Signal signal;

  private String event;

  @Lob private String payload;
}
