package com.arbitrage.dto.processor;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.signal.TradeSignalDto;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
public final class SignalContext {

  private final TradeSignalDto dto;
  private final UUID savedSignalId;
  private final Clock clock;

  @Setter private List<ResolvedLegDto> resolvedLegs;

  private SignalContext(TradeSignalDto dto, Clock clock, UUID savedSignalId) {
    this.dto = Objects.requireNonNull(dto, "dto");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.savedSignalId = Objects.requireNonNull(savedSignalId, "savedSignalId");
  }

  public static SignalContext of(TradeSignalDto dto, Clock clock, UUID savedSignalId) {
    return new SignalContext(dto, clock, savedSignalId);
  }
}
