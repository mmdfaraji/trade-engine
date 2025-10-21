package com.arbitrage.service.mapping;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.Pair;
import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalLeg;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.enums.TimeInForce;

import java.math.BigDecimal;

public class SignalAssembler {

    private SignalAssembler() {
    }

/*    public static Signal toSignalEntity(SignalMessageDto dto, String constraintsJson) {
        return Signal.builder()
                .externalId(dto.getSignalId())
                .ttlMs(dto.getTtlMs())
                .status(SignalStatus.RECEIVED)
                .source(dto.getSource())
                .constraints(constraintsJson)
                .expectedPnl(dto.getExpectedPnl())
                .producerCreatedAt(dto.getCreatedAt())
                .build();
    }*/

    public static SignalLeg toLegEntity(ResolvedLegDto rl, Signal signal, Exchange exchange, Pair pair,
                                        TimeInForce tif, String desiredRole) {
        return SignalLeg.builder()
                .signal(signal)
                .exchange(exchange)
                .pair(pair)
                .side(rl.getSide().name())
                .price(rl.getPrice())
                .qty(rl.getQty())
                .tif(tif != null ? tif.name() : null)
                .desiredRole(desiredRole)
                .build();
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
