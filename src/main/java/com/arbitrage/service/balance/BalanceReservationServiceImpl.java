package com.arbitrage.service.balance;

import com.arbitrage.dto.SignalLegDto;
import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.balance.BucketKeyDto;
import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.plan.BalanceReservationResultDto;
import com.arbitrage.dto.plan.ExecutionLegPlanDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.entities.BalanceLock;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.entities.Pair;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.repository.BalanceLockRepository;
import com.arbitrage.repository.BalanceRepository;
import com.arbitrage.repository.CurrencyRepository;
import com.arbitrage.repository.ExchangeAccountRepository;
import com.arbitrage.repository.ExchangeRepository;
import com.arbitrage.repository.PairRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceReservationServiceImpl implements BalanceReservationService {

  private final ExchangeRepository exchangeRepository;
  private final PairRepository pairRepository;
  private final ExchangeAccountRepository exchangeAccountRepository;
  private final CurrencyRepository currencyRepository;
  private final BalanceRepository balanceRepository;
  private final BalanceLockRepository balanceLockRepository;
  private final Clock clock;

  @Override
  @Transactional
  public BalanceReservationResultDto reserveForSignal(SignalContext ctx) {
    // Inputs
    SignalMessageDto dto = ctx.getDto();
    List<SignalLegDto> legs = dto.getLegs() != null ? dto.getLegs() : Collections.emptyList();
    int n = legs.size();
    if (n == 0) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("No legs provided")
              .phase(ValidationPhase.PHASE2_BALANCE)
              .validator("BalanceReservationService")
              .occurredAt(clock.instant())
              .build();
      return new BalanceReservationResultDto(StepResult.fail(rej), null);
    }

    // Resolve each leg
    List<ResolvedLegDto> resolved = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      SignalLegDto leg = legs.get(i);

      // Exchange by name (ignore-case)
      Exchange ex =
          exchangeRepository
              .findByNameEqualsIgnoreCase(leg.getExchange())
              .orElseThrow(
                  () -> new IllegalArgumentException("Unknown exchange: " + leg.getExchange()));

      // Pair by symbol (internal standard; ignore-case)
      Pair pair =
          pairRepository
              .findBySymbolEqualsIgnoreCase(leg.getMarket())
              .orElseThrow(
                  () -> new IllegalArgumentException("Unknown pair symbol: " + leg.getMarket()));

      OrderSide side = toSide(leg.getSide());

      Long baseId = pair.getBaseCurrency().getId();
      Long quoteId = pair.getQuoteCurrency().getId();
      Long spendCurId = side == OrderSide.BUY ? quoteId : baseId;
      Long receiveCurId = side == OrderSide.BUY ? baseId : quoteId;

      Long accountId =
          exchangeAccountRepository
              .findFirstByExchange_IdAndIsPrimaryTrue(ex.getId())
              .map(ExchangeAccount::getId)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No primary account for exchange: " + ex.getName()));

      BigDecimal qty = nz(leg.getQty());
      BigDecimal price = nz(leg.getPrice());
      BigDecimal requiredSpend = side == OrderSide.BUY ? qty.multiply(price) : qty;

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
              .marketSymbol(leg.getMarket())
              .side(side)
              .qty(qty)
              .price(price)
              .requiredSpend(requiredSpend)
              .build();

      resolved.add(rl);
    }

    // Aggregate by bucket (accountId, spendCurrencyId)
    Map<BucketKeyDto, BigDecimal> bucketAmounts = new LinkedHashMap<>();
    for (ResolvedLegDto rl : resolved) {
      BucketKeyDto key = new BucketKeyDto(rl.getExchangeAccountId(), rl.getSpendCurrencyId());
      BigDecimal sum = bucketAmounts.get(key);
      sum = sum == null ? rl.getRequiredSpend() : sum.add(rl.getRequiredSpend());
      bucketAmounts.put(key, sum);
    }

    // Compute lock expiry
    Instant expiresAt = computeExpireInstant(dto);

    // Try reserve all buckets atomically (all-or-nothing)
    for (Map.Entry<BucketKeyDto, BigDecimal> e : bucketAmounts.entrySet()) {
      BucketKeyDto key = e.getKey();
      BigDecimal amount = e.getValue();

      // Idempotent lock insert
      try {
        BalanceLock lock =
            BalanceLock.builder()
                .exchangeAccount(exchangeAccountRepository.getReferenceById(key.getAccountId()))
                .currency(currencyRepository.getReferenceById(key.getCurrencyId()))
                .amount(amount)
                .reason("SIGNAL_RESERVE")
                .signalId(ctx.getSavedSignalId().toString())
                .expiresAt(java.util.Date.from(expiresAt))
                .build();
        balanceLockRepository.save(lock);
      } catch (DataIntegrityViolationException duplicate) {
        // Idempotent: existing lock for (account, currency, signal)
      }

      // Conditional update (available >= amount)
      int updated = balanceRepository.tryReserve(key.getAccountId(), key.getCurrencyId(), amount);
      if (updated == 0) {
        // rollback
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        Rejection rej =
            Rejection.builder()
                .code(RejectCode.INSUFFICIENT_FUNDS)
                .message("Insufficient available balance")
                .phase(ValidationPhase.PHASE2_BALANCE)
                .validator("BalanceReservationService")
                .occurredAt(clock.instant())
                .detail("accountId", key.getAccountId())
                .detail("currencyId", key.getCurrencyId())
                .detail("required", amount)
                .build();

        log.warn(
            "Balance reservation rejected: externalId={}, signalId={}, accountId={}, currencyId={}, required={}",
            dto.getSignalId(),
            ctx.getSavedSignalId(),
            key.getAccountId(),
            key.getCurrencyId(),
            amount);
        return new BalanceReservationResultDto(StepResult.fail(rej), buildPlan(resolved, false));
      }
    }

    // Success
    ExecutionPlanDto plan = buildPlan(resolved, true);
    log.info(
        "Balance & reservation passed: externalId={}, signalId={}, legs={}, buckets={}",
        dto.getSignalId(),
        ctx.getSavedSignalId(),
        n,
        bucketAmounts.size());
    return new BalanceReservationResultDto(StepResult.pass(), plan);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static OrderSide toSide(String sideText) {
    if (sideText == null) return OrderSide.BUY;
    String s = sideText.trim().toUpperCase(Locale.ROOT);
    return "SELL".equals(s) ? OrderSide.SELL : OrderSide.BUY;
  }

  private Instant computeExpireInstant(SignalMessageDto dto) {
    Instant base = dto.getCreatedAt() != null ? dto.getCreatedAt() : clock.instant();
    long ttl = dto.getTtlMs() != null ? dto.getTtlMs() : 0L;
    return base.plusMillis(ttl);
  }

  private ExecutionPlanDto buildPlan(List<ResolvedLegDto> resolved, boolean success) {
    ExecutionPlanDto.ExecutionPlanDtoBuilder builder = ExecutionPlanDto.builder();
    for (ResolvedLegDto rl : resolved) {
      ExecutionLegPlanDto leg =
          ExecutionLegPlanDto.builder()
              .index(rl.getIndex())
              .exchangeId(rl.getExchangeId())
              .exchangeAccountId(rl.getExchangeAccountId())
              .pairId(rl.getPairId())
              .baseCurrencyId(rl.getBaseCurrencyId())
              .quoteCurrencyId(rl.getQuoteCurrencyId())
              .spendCurrencyId(rl.getSpendCurrencyId())
              .receiveCurrencyId(rl.getReceiveCurrencyId())
              .exchangeName(rl.getExchangeName())
              .marketSymbol(rl.getMarketSymbol())
              .side(rl.getSide())
              .reqQty(rl.getQty())
              .price(rl.getPrice())
              .requiredSpend(rl.getRequiredSpend())
              .execQty(success ? rl.getQty() : BigDecimal.ZERO)
              .build();
      builder.leg(leg);
    }
    return builder.build();
  }
}
