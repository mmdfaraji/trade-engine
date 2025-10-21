package com.arbitrage.entities;

import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.SignalEventType;
import com.arbitrage.enums.ValidationPhase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "signal_events",
    indexes = {
      @Index(name = "idx_signal_events_signal_id", columnList = "signal_id"),
      @Index(name = "idx_signal_events_type_created_at", columnList = "type, createdAt"),
      @Index(name = "idx_signal_events_external_id", columnList = "externalId")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignalEvent extends LongIdEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "signal_id")
  private Signal signal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 48)
  private SignalEventType type;

  @Enumerated(EnumType.STRING)
  @Column(length = 48)
  private ValidationPhase phase; // nullable for RECEIVED/ACCEPTED

  @Enumerated(EnumType.STRING)
  @Column(length = 64)
  private RejectCode rejectCode; // nullable unless *_FAILED

  @Column(length = 512)
  private String message; // short human-readable message

  @Column(length = 128)
  private String externalId; // denormalized for quick lookup (meta.signalId)

  @Lob private String payload; // raw dto or rejection details as JSON
}
