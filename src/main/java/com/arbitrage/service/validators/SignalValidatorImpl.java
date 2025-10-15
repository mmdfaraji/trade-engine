package com.arbitrage.service.validators;

import com.arbitrage.dto.balance.BalanceValidationReportDto;
import com.arbitrage.dto.balance.BucketKeyDto;
import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.plan.ExecutionLegPlanDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.entities.Balance;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.repository.BalanceRepository;
import com.arbitrage.repository.ExchangeAccountRepository;
import com.arbitrage.repository.ExchangeRepository;
import com.arbitrage.repository.PairRepository;
import com.arbitrage.service.api.SignalValidator;
import com.arbitrage.service.validators.guard.MetaIntegrityService;
import com.arbitrage.service.validators.guard.OrdersIntegrityService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalValidatorImpl implements SignalValidator {

  private final ExchangeRepository exchangeRepository;
  private final PairRepository pairRepository;
  private final ExchangeAccountRepository exchangeAccountRepository;
  private final BalanceRepository balanceRepository;
  private final MetaIntegrityService metaIntegrityService;
  private final OrdersIntegrityService ordersIntegrityService;
  private final Clock clock;

  @Override
  public StepResult validateIntegrity(SignalContext ctx) {
    String sigId = ctx.getDto().getMeta() != null ? ctx.getDto().getMeta().getSignalId() : "n/a";

    StepResult metaRes = metaIntegrityService.validate(ctx.getDto().getMeta());
    if (!metaRes.isOk()) {
      return metaRes;
    }

    StepResult ordersRes =
        ordersIntegrityService.validateAndResolve(ctx.getDto().getOrders(), sigId, ctx);
    if (!ordersRes.isOk()) {
      return ordersRes;
    }

    log.info("Integrity ok: signalId={}", sigId);
    return StepResult.ok();
  }

  @Override
  public StepResult validateFreshness(SignalContext ctx) {
    TradeSignalDto dto = ctx.getDto();

    if (dto.getMeta() == null) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing meta")
              .phase(ValidationPhase.PHASE1_FRESHNESS)
              .validator("SignalValidator")
              .occurredAt(clock.instant())
              .build();
      log.warn("Freshness invalid: signalId=n/a, reason=missing_meta");
      return StepResult.fail(rej);
    }

    String sigId = dto.getMeta().getSignalId();
    java.time.Instant createdAt = dto.getMeta().getCreatedAt();
    Long ttlMs = dto.getMeta().getTtlMs();

    if (createdAt == null || ttlMs == null) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Missing createdAt or ttlMs in meta")
              .phase(ValidationPhase.PHASE1_FRESHNESS)
              .validator("SignalValidator")
              .occurredAt(clock.instant())
              .detail("signalId", sigId)
              .build();
      log.warn("Freshness invalid: signalId={}, reason=missing_fields", sigId);
      return StepResult.fail(rej);
    }

    long age = java.time.Duration.between(createdAt, clock.instant()).toMillis();
    if (age > ttlMs) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Signal expired: age_ms > ttl_ms")
              .phase(ValidationPhase.PHASE1_FRESHNESS)
              .validator("SignalValidator")
              .occurredAt(clock.instant())
              .detail("signalId", sigId)
              .detail("age_ms", age)
              .detail("ttl_ms", ttlMs)
              .build();
      log.warn("Freshness expired: signalId={}, ageMs={}, ttlMs={}", sigId, age, ttlMs);
      return StepResult.fail(rej);
    }

    log.info("Freshness ok: signalId={}, ageMs={}, ttlMs={}", sigId, age, ttlMs);
    return StepResult.ok();
  }

  @Override
  public BalanceValidationReportDto validateBalance(SignalContext ctx) {
    String sigId = ctx.getDto().getMeta() != null ? ctx.getDto().getMeta().getSignalId() : "n/a";
    List<ResolvedLegDto> resolved = ctx.getResolvedLegs();
    if (resolved == null || resolved.size() != 2) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.INVALID_INPUT)
              .message("Resolved legs missing or invalid (integrity not applied?)")
              .phase(ValidationPhase.PHASE2_BALANCE)
              .validator("SignalValidator")
              .occurredAt(clock.instant())
              .detail("signalId", sigId)
              .build();
      log.warn(
          "Balance validation guard: signalId={}, resolvedLegs={}",
          sigId,
          resolved == null ? 0 : resolved.size());
      return BalanceValidationReportDto.builder().result(StepResult.fail(rej)).build();
    }

    Map<BucketKeyDto, BigDecimal> bucketAmounts = new LinkedHashMap<>();
    Set<Long> accountIds = new LinkedHashSet<>();
    Set<Long> currencyIds = new LinkedHashSet<>();

    for (ResolvedLegDto rl : resolved) {
      BucketKeyDto key = new BucketKeyDto(rl.getExchangeAccountId(), rl.getSpendCurrencyId());
      BigDecimal sum = bucketAmounts.get(key);
      sum = (sum == null) ? rl.getRequiredSpend() : sum.add(rl.getRequiredSpend());
      bucketAmounts.put(key, sum);
      accountIds.add(key.getAccountId());
      currencyIds.add(key.getCurrencyId());
    }

    List<Balance> balances =
        balanceRepository.findByExchangeAccount_IdInAndCurrency_IdIn(accountIds, currencyIds);

    for (Map.Entry<BucketKeyDto, BigDecimal> e : bucketAmounts.entrySet()) {
      BucketKeyDto key = e.getKey();
      BigDecimal need = e.getValue();

      Optional<Balance> balOpt =
          balances.stream()
              .filter(
                  b ->
                      b.getExchangeAccount().getId().equals(key.getAccountId())
                          && b.getCurrency().getId().equals(key.getCurrencyId()))
              .findFirst();

      BigDecimal available = balOpt.map(Balance::getAvailable).orElse(BigDecimal.ZERO);
      if (available.compareTo(need) < 0) {
        Rejection rej =
            Rejection.builder()
                .code(RejectCode.INSUFFICIENT_FUNDS)
                .message("Insufficient balance for bucket")
                .phase(ValidationPhase.PHASE2_BALANCE)
                .validator("SignalValidator")
                .occurredAt(clock.instant())
                .detail("accountId", key.getAccountId())
                .detail("currencyId", key.getCurrencyId())
                .detail("required", need)
                .detail("available", available)
                .detail("signalId", sigId)
                .build();
        log.warn(
            "Balance insufficient: signalId={}, accountId={}, currencyId={}, required={}, available={}",
            sigId,
            key.getAccountId(),
            key.getCurrencyId(),
            need,
            available);
        return BalanceValidationReportDto.builder().result(StepResult.fail(rej)).build();
      }
    }

    ExecutionPlanDto.ExecutionPlanDtoBuilder pb = ExecutionPlanDto.builder();
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
              .execQty(rl.getQty())
              .build();
      pb.leg(leg);
    }

    ExecutionPlanDto plan = pb.build();
    log.info(
        "Balance ok: signalId={}, legs={}, buckets={}",
        sigId,
        resolved.size(),
        bucketAmounts.size());
    return BalanceValidationReportDto.builder()
        .result(StepResult.ok())
        .plan(plan)
        .bucketAmounts(bucketAmounts)
        .build();
  }

  private static ExecutionPlanDto buildPlan(List<ResolvedLegDto> resolved) {
    ExecutionPlanDto.ExecutionPlanDtoBuilder pb = ExecutionPlanDto.builder();
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
              .execQty(rl.getQty())
              .build();
      pb.leg(leg);
    }
    return pb.build();
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }
}
