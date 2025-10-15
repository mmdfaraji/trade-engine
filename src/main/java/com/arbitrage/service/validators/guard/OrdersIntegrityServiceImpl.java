package com.arbitrage.service.validators.guard;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.OrderInstructionDto;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.entities.Pair;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.repository.ExchangeAccountRepository;
import com.arbitrage.repository.ExchangeRepository;
import com.arbitrage.repository.PairRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdersIntegrityServiceImpl implements OrdersIntegrityService {

  private final Clock clock;
  private final ExchangeRepository exchangeRepository;
  private final PairRepository pairRepository;
  private final ExchangeAccountRepository exchangeAccountRepository;

  @Override
  public StepResult validateAndResolve(
      List<OrderInstructionDto> orders, String signalIdForLog, SignalContext ctx) {
    if (orders == null || orders.size() != 2) {
      return fail("Leg count must be exactly 2", signalIdForLog, -1);
    }

    List<ResolvedLegDto> resolved = new ArrayList<>(2);
    for (int i = 0; i < orders.size(); i++) {
      OrderInstructionDto leg = orders.get(i);

      if (isBlank(leg.getExchangeName())) return fail("Missing exchangeName", signalIdForLog, i);
      if (isBlank(leg.getPairName())) return fail("Missing pairName", signalIdForLog, i);
      if (leg.getSide() == null) return fail("Missing side", signalIdForLog, i);

      BigDecimal price = leg.getPriceAsBigDecimal();
      if (price == null || price.signum() <= 0)
        return fail("Missing/invalid price", signalIdForLog, i);

      BigDecimal baseQty = leg.getBaseAmountAsBigDecimal();
      if (baseQty == null || baseQty.signum() <= 0)
        return fail("Missing/invalid baseAmount", signalIdForLog, i);

      Exchange ex =
          exchangeRepository.findByNameEqualsIgnoreCase(leg.getExchangeName()).orElse(null);
      if (ex == null) return fail("Unknown exchange", signalIdForLog, i);

      Pair pair = pairRepository.findBySymbolEqualsIgnoreCase(leg.getPairName()).orElse(null);
      if (pair == null) return fail("Unknown pair", signalIdForLog, i);

      Long accountId =
          exchangeAccountRepository
              .findFirstByExchange_IdAndIsPrimaryTrue(ex.getId())
              .map(ExchangeAccount::getId)
              .orElse(null);
      if (accountId == null) return fail("No primary account for exchange", signalIdForLog, i);

      OrderSide side = leg.getSide();
      Long baseId = pair.getBaseCurrency().getId();
      Long quoteId = pair.getQuoteCurrency().getId();
      Long spendCurId = side == OrderSide.BUY ? quoteId : baseId;
      Long receiveCurId = side == OrderSide.BUY ? baseId : quoteId;

      ResolvedLegDto rl =
          ResolvedLegDto.builder()
              .index(i)
              .exchangeId(ex.getId())
              .exchangeName(ex.getName())
              .exchangeAccountId(accountId)
              .pairId(pair.getId())
              .baseCurrencyId(baseId)
              .quoteCurrencyId(quoteId)
              .spendCurrencyId(spendCurId)
              .receiveCurrencyId(receiveCurId)
              .marketSymbol(leg.getPairName())
              .side(side)
              .qty(baseQty)
              .price(price)
              .requiredSpend(side == OrderSide.BUY ? baseQty.multiply(price) : baseQty)
              .build();

      resolved.add(rl);
    }

    ctx.setResolvedLegs(resolved);
    log.info("Orders integrity+resolve ok: signalId={}, legs=2", signalIdForLog);
    return StepResult.ok();
  }

  private StepResult fail(String msg, String sigId, int legIndex) {
    Rejection rej =
        Rejection.builder()
            .code(RejectCode.INVALID_INPUT)
            .message(msg)
            .phase(ValidationPhase.PHASE0_INTEGRITY)
            .validator("OrdersIntegrityService")
            .occurredAt(clock.instant())
            .detail("signalId", sigId)
            .detail("legIndex", legIndex)
            .build();
    log.warn("Integrity failed: signalId={}, legIndex={}, reason={}", sigId, legIndex, msg);
    return StepResult.fail(rej);
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
