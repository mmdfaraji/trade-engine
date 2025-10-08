package com.arbitrage.service;

import com.arbitrage.dto.SignalLegDto;
import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.Pair;
import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalLeg;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.repository.SignalLegRepository;
import com.arbitrage.repository.SignalRepository;
import com.arbitrage.service.api.ExchangeService;
import com.arbitrage.service.api.PairService;
import com.arbitrage.service.api.SignalService;
import com.arbitrage.service.mapping.SignalAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
  private final ExchangeService exchangeService;
  private final PairService pairService;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public UUID saveSignal(SignalMessageDto dto) {
    if (dto.getSignalId() != null) {
      var existing = signalRepository.findByExternalId(dto.getSignalId());
      if (existing.isPresent()) {
        Signal s = existing.get();
        log.info(
            "Signal already exists (idempotent): externalId={}, id={}, status={}",
            dto.getSignalId(),
            s.getId(),
            s.getStatus());
        return s.getId();
      }
    }

    String constraintsJson = serializeConstraints(dto);
    Signal signal = SignalAssembler.toSignalEntity(dto, constraintsJson);
    signal = signalRepository.save(signal);

    if (dto.getLegs() != null) {
      int i = 0;
      for (SignalLegDto legDto : dto.getLegs()) {
        Exchange exchange = exchangeService.resolveExchangeByName(legDto.getExchange());
        Pair pair = pairService.resolvePairBySymbol(legDto.getMarket());

        SignalLeg leg = SignalAssembler.toLegEntity(legDto, signal, exchange, pair);
        signalLegRepository.save(leg);

        log.info(
            "Saved leg#{} for signalId={} -> exchange={}, market={}, side={}, price={}, qty={}, tif={}",
            i++,
            signal.getId(),
            legDto.getExchange(),
            legDto.getMarket(),
            legDto.getSide(),
            legDto.getPrice(),
            legDto.getQty(),
            legDto.getTimeInForce());
      }
    }

    log.info(
        "Saved signal: id={}, externalId={}, ttlMs={}, createdAt={}, legsCount={}, source={}",
        signal.getId(),
        dto.getSignalId(),
        dto.getTtlMs(),
        dto.getCreatedAt(),
        dto.getLegs() == null ? 0 : dto.getLegs().size(),
        dto.getSource());

    return signal.getId();
  }

  @Override
  @Transactional
  public void updateStatus(UUID signalId, SignalStatus newStatus) {
    Signal signal =
        signalRepository
            .findById(signalId)
            .orElseThrow(() -> new IllegalArgumentException("Signal not found: " + signalId));
    try {
      signal.setStatus(newStatus);
      signalRepository.save(signal);
      log.info("Signal status updated: id={}, status={}", signalId, newStatus);
    } catch (IllegalArgumentException iae) {
      log.warn(
          "Invalid status value provided for update: id={}, newStatus={}", signalId, newStatus);
      throw iae;
    }
  }

  private String serializeConstraints(SignalMessageDto dto) {
    try {
      if (dto.getConstraints() == null) return null;
      byte[] json = objectMapper.writeValueAsBytes(dto.getConstraints());
      return new String(json, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn(
          "Constraints serialization failed, storing null. externalId={}", dto.getSignalId(), e);
      return null;
    }
  }
}
