package com.arbitrage.service;

import com.arbitrage.dto.signal.MetaDto;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.entities.Signal;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.repository.SignalRepository;
import com.arbitrage.service.api.SignalService;
import java.util.Optional;
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

  @Override
  @Transactional
  public UUID saveSignal(TradeSignalDto dto) {
    if (dto == null) {
      throw new IllegalArgumentException("dto is null");
    }

    MetaDto meta = dto.getMeta();
    String externalId = (meta != null ? meta.getSignalId() : null);
    if (externalId != null && !externalId.isBlank()) {
      Optional<Signal> existing = signalRepository.findByExternalId(externalId);
      if (existing.isPresent()) {
        Signal s = existing.get();
        log.info(
            "Signal idempotent hit: externalId={}, id={}, status={}",
            externalId,
            s.getId(),
            s.getStatus());
        return s.getId();
      }
    }

    Signal signal =
        Signal.builder()
            .externalId(externalId)
            .ttlMs(meta != null ? meta.getTtlMs() : null)
            .status(SignalStatus.RECEIVED)
            .source(dto.getClazz()) // optional: class as source tag
            .constraints(null) // no constraints in this payload (kept null)
            .expectedPnl(null) // not provided at ingest
            .producerCreatedAt(meta != null ? meta.getCreatedAt() : null)
            .build();

    signal = signalRepository.save(signal);

    int legsCount = (dto.getOrders() == null ? 0 : dto.getOrders().size());
    String pair = (meta != null ? meta.getPair() : null);
    log.info(
        "Saved signal: id={}, externalId={}, ttlMs={}, producerCreatedAt={}, legsCount={}, pair={}",
        signal.getId(),
        externalId,
        signal.getTtlMs(),
        signal.getProducerCreatedAt(),
        legsCount,
        pair);

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
}
