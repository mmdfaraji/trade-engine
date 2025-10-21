// start line of new codes
package com.arbitrage.service;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.signal.OrderInstructionDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.Pair;
import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalLeg;
import com.arbitrage.enums.TimeInForce;
import com.arbitrage.repository.ExchangeRepository;
import com.arbitrage.repository.PairRepository;
import com.arbitrage.repository.SignalLegRepository;
import com.arbitrage.repository.SignalRepository;
import com.arbitrage.service.api.SignalLegService;
import com.arbitrage.service.mapping.SignalAssembler;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persists normalized legs after PHASE0_INTEGRITY. Uses JPA reference lookups to avoid extra
 * SELECTs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalLegServiceImpl implements SignalLegService {

  private final SignalRepository signalRepository;
  private final SignalLegRepository signalLegRepository;
  private final ExchangeRepository exchangeRepository;
  private final PairRepository pairRepository;

  @Override
  @Transactional
  public void persistResolved(
      UUID signalId, List<ResolvedLegDto> resolved, List<OrderInstructionDto> originalOrders) {
    if (signalId == null || resolved == null || resolved.isEmpty()) {
      return;
    }
    Signal signalRef = signalRepository.getReferenceById(signalId);

    for (ResolvedLegDto rl : resolved) {
      Exchange exRef = exchangeRepository.getReferenceById(rl.getExchangeId());
      Pair pairRef = pairRepository.getReferenceById(rl.getPairId());

      // Optional: map TIF / desiredRole from the original order (by index)
      TimeInForce tif = null;
      String desiredRole = null;
      if (originalOrders != null && rl.getIndex() < originalOrders.size()) {
        OrderInstructionDto src = originalOrders.get(rl.getIndex());
        tif = src.getTimeInForce();
        //                desiredRole = src.getDesiredRole();
      }

      SignalLeg leg = SignalAssembler.toLegEntity(rl, signalRef, exRef, pairRef, tif, desiredRole);
      signalLegRepository.save(leg);

      log.info(
          "Saved leg idx={} signalId={} -> exchangeId={}, pairId={}, side={}, price={}, qty={}, tif={}",
          rl.getIndex(),
          signalId,
          rl.getExchangeId(),
          rl.getPairId(),
          rl.getSide(),
          rl.getPrice(),
          rl.getQty(),
          tif == null ? null : tif.name());
    }
  }
}
// end line of new codes
