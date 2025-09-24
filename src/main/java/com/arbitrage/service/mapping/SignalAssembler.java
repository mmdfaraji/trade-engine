package com.arbitrage.service.mapping;

import com.arbitrage.dto.SignalLegDto;
import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.Pair;
import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalLeg;
import com.arbitrage.enums.SignalStatus;
import java.math.BigDecimal;

public class SignalAssembler {

  private SignalAssembler() {}

  public static Signal toSignalEntity(SignalMessageDto dto, String constraintsJson) {
    return Signal.builder()
        .externalId(dto.getSignalId())
        .ttlMs(dto.getTtlMs())
        .status(SignalStatus.RECEIVED)
        .source(dto.getSource())
        .constraints(constraintsJson)
        .expectedPnl(dto.getExpectedPnl())
        .producerCreatedAt(dto.getCreatedAt())
        .build();
  }

  public static SignalLeg toLegEntity(
      SignalLegDto legDto, Signal signal, Exchange exchange, Pair pair) {
    return SignalLeg.builder()
        .signal(signal)
        .exchange(exchange)
        .pair(pair)
        .side(legDto.getSide())
        .price(nullSafe(legDto.getPrice()))
        .qty(nullSafe(legDto.getQty()))
        .tif(legDto.getTimeInForce())
        .desiredRole(legDto.getDesiredRole())
        .build();
  }

  private static BigDecimal nullSafe(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
