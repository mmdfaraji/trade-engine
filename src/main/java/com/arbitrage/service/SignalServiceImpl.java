package com.arbitrage.service;

import com.arbitrage.dto.SignalLegDto;
import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.Pair;
import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalLeg;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.respository.ExchangeRepository;
import com.arbitrage.respository.PairRepository;
import com.arbitrage.respository.SignalLegRepository;
import com.arbitrage.respository.SignalRepository;
import com.arbitrage.service.api.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalServiceImpl implements SignalService {
  private final SignalRepository signalRepository;
  private final SignalLegRepository signalLegRepository;
  private final ExchangeRepository exchangeRepository;
  private final PairRepository pairRepository;

  @Transactional
  @Override
  public Long saveSignal(SignalMessageDto dto) {
    Signal signal =
        Signal.builder()
            .ttlMs(dto.getTtlMs())
            .status(dto.getStatus() != null ? dto.getStatus() : SignalStatus.RECEIVED)
            .source(dto.getSource())
            .constraints(dto.getConstraints())
            .expectedPnl(dto.getExpectedPnl())
            .build();
    signal = signalRepository.save(signal);
    log.info("signal saved :{}", signal);

    if (dto.getLegs() != null) {
      for (SignalLegDto legDTO : dto.getLegs()) {
        Exchange exchange =
            exchangeRepository
                .findByName(legDTO.getExchangeName())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Exchange not found: " + legDTO.getExchangeName()));
        Pair pair =
            pairRepository
                .findBySymbol(legDTO.getPairSymbol())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException("Pair not found: " + legDTO.getPairSymbol()));

        SignalLeg leg =
            SignalLeg.builder()
                .signal(signal)
                .exchange(exchange)
                .pair(pair)
                .side(legDTO.getSide())
                .price(legDTO.getPrice())
                .qty(legDTO.getQty())
                .tif(legDTO.getTif())
                .desiredRole(legDTO.getDesiredRole())
                .build();
        signalLegRepository.save(leg);
      }
    }
    return signal.getId();
  }
}
