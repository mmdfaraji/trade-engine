package com.arbitrage.service.validators;

import com.arbitrage.dto.balance.BalanceValidationReportDto;
import com.arbitrage.dto.balance.BucketKeyDto;
import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.balance.SizingResultDto;
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
import com.arbitrage.service.api.SignalValidator;
import com.arbitrage.service.balance.ExecutionSizingService;
import com.arbitrage.service.validators.guard.MetaIntegrityService;
import com.arbitrage.service.validators.guard.OrdersIntegrityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalValidatorImpl implements SignalValidator {

    private final BalanceRepository balanceRepository;
    private final MetaIntegrityService metaIntegrityService;
    private final OrdersIntegrityService ordersIntegrityService;
    private final ExecutionSizingService executionSizingService;
    private final Clock clock;

    @Override
    public StepResult validateIntegrity(SignalContext ctx) {
        String sigId = (ctx.getDto().getMeta() != null) ? ctx.getDto().getMeta().getSignalId() : "n/a";

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
                            .code(RejectCode.INTEGRITY_MISSING_META)
                            .message("Missing meta")
                            .phase(ValidationPhase.PHASE1_FRESHNESS)
                            .validator("SignalValidator")
                            .occurredAt(clock.instant())
                            .build();
            log.warn("Freshness invalid: missing meta");
            return StepResult.fail(rej);
        }

        String sigId = dto.getMeta().getSignalId();
        Instant createdAt = dto.getMeta().getCreatedAt();
        Long ttlMs = dto.getMeta().getTtlMs();
        Long maxLatencyMs = dto.getMeta().getMaxLatencyMs();

        if (createdAt == null || ttlMs == null || ttlMs <= 0) {
            Rejection rej =
                    Rejection.builder()
                            .code(RejectCode.INTEGRITY_MISSING_FIELD)
                            .message("Missing/invalid createdAt or ttlMs")
                            .phase(ValidationPhase.PHASE1_FRESHNESS)
                            .validator("SignalValidator")
                            .occurredAt(clock.instant())
                            .detail("signalId", sigId)
                            .build();
            log.warn("Freshness invalid: signalId={}, missing/invalid fields", sigId);
            return StepResult.fail(rej);
        }

        long ageMs = Duration.between(createdAt, clock.instant()).toMillis();
        if (ageMs > ttlMs) {
            Rejection rej =
                    Rejection.builder()
                            .code(RejectCode.STALE)
                            .message("Signal expired: age_ms > ttl_ms")
                            .phase(ValidationPhase.PHASE1_FRESHNESS)
                            .validator("SignalValidator")
                            .occurredAt(clock.instant())
                            .detail("signalId", sigId)
                            .detail("age_ms", ageMs)
                            .detail("ttl_ms", ttlMs)
                            .build();
            log.warn("Freshness expired: signalId={}, ageMs={}, ttlMs={}", sigId, ageMs, ttlMs);
            return StepResult.fail(rej);
        }

        if (maxLatencyMs != null && maxLatencyMs > 0 && ageMs > maxLatencyMs) {
            Rejection rej =
                    Rejection.builder()
                            .code(RejectCode.STALE)
                            .message("Latency guard failed: age_ms > max_latency_ms")
                            .phase(ValidationPhase.PHASE1_FRESHNESS)
                            .validator("SignalValidator")
                            .occurredAt(clock.instant())
                            .detail("signalId", sigId)
                            .detail("age_ms", ageMs)
                            .detail("max_latency_ms", maxLatencyMs)
                            .build();
            log.warn(
                    "Freshness latency fail: signalId={}, ageMs={}, maxLatencyMs={}",
                    sigId,
                    ageMs,
                    maxLatencyMs);
            return StepResult.fail(rej);
        }

        log.info("Freshness ok: signalId={}, ageMs={}, ttlMs={}", sigId, ageMs, ttlMs);
        return StepResult.ok();
    }

    @Override
    public BalanceValidationReportDto validateBalance(SignalContext ctx) {
        TradeSignalDto dto = ctx.getDto();
        String sigId = (dto.getMeta() != null) ? dto.getMeta().getSignalId() : "n/a";

        List<ResolvedLegDto> resolved = ctx.getResolvedLegs();
        if (resolved == null || resolved.size() != 2) {
            Rejection rej = Rejection.builder()
                    .code(RejectCode.INVALID_INPUT)
                    .message("Resolved legs missing or invalid (integrity not applied?)")
                    .phase(ValidationPhase.PHASE2_BALANCE)
                    .validator("SignalValidator")
                    .occurredAt(clock.instant())
                    .detail("signalId", sigId)
                    .detail("legs", resolved == null ? 0 : resolved.size())
                    .build();
            log.warn("balance guard: signalId={}, resolvedLegs={}", sigId, resolved == null ? 0 : resolved.size());
            return BalanceValidationReportDto.builder().result(StepResult.fail(rej)).build();
        }

        // Aggregate required spend per (account,currency) bucket
        Map<BucketKeyDto, BigDecimal> bucketAmounts = new LinkedHashMap<>();
        Set<Long> accountIds = new LinkedHashSet<>();
        Set<Long> currencyIds = new LinkedHashSet<>();

        for (ResolvedLegDto rl : resolved) {
            BucketKeyDto key = new BucketKeyDto(rl.getExchangeAccountId(), rl.getSpendCurrencyId());
            BigDecimal prev = bucketAmounts.get(key);
            BigDecimal add = rl.getRequiredSpend();
            bucketAmounts.put(key, prev == null ? add : prev.add(add));
            accountIds.add(key.getAccountId());
            currencyIds.add(key.getCurrencyId());
        }

        // Load balances for all buckets
        List<Balance> balances =
                balanceRepository.findByExchangeAccount_IdInAndCurrency_IdIn(accountIds, currencyIds);

        // Map available balances by bucket
        Map<BucketKeyDto, BigDecimal> availableByBucket = new HashMap<>();
        for (Balance b : balances) {
            BucketKeyDto key = new BucketKeyDto(b.getExchangeAccount().getId(), b.getCurrency().getId());
            availableByBucket.put(key, b.getAvailable() == null ? BigDecimal.ZERO : b.getAvailable());
        }

        // Compute sizing alpha and per-leg execQty
        SizingResultDto sizing =
                executionSizingService.sizeForBalances(resolved, availableByBucket);

        if (sizing.getScaleRatio() == null || sizing.getScaleRatio().signum() <= 0) {
            Rejection rej = Rejection.builder()
                    .code(RejectCode.INSUFFICIENT_FUNDS)
                    .message("No executable size from balances")
                    .phase(ValidationPhase.PHASE2_BALANCE)
                    .validator("SignalValidator")
                    .occurredAt(clock.instant())
                    .detail("signalId", sigId)
                    .detail("alpha", sizing.getScaleRatio() == null ? "null" : sizing.getScaleRatio())
                    .build();
            log.warn("balance insufficient: signalId={}, alpha<=0", sigId);
            return BalanceValidationReportDto.builder().result(StepResult.fail(rej)).build();
        }

        // Build execution plan using sized execQty
        ExecutionPlanDto plan = buildPlan(resolved, sizing.getExecQty());

        if (sizing.isScaled()) {
            log.info(
                    "balance scaled: signalId={}, alpha={}, leg0 {}->{} , leg1 {}->{}",
                    sigId,
                    sizing.getScaleRatio(),
                    resolved.get(0).getQty(), sizing.getExecQty().get(0),
                    resolved.get(1).getQty(), sizing.getExecQty().get(1));
        } else {
            log.info(
                    "balance ok: signalId={}, legs={}, buckets={}",
                    sigId, resolved.size(), bucketAmounts.size());
        }

        return BalanceValidationReportDto.builder()
                .result(StepResult.ok())
                .plan(plan)
                .bucketAmounts(bucketAmounts) // requested spend per bucket (pre-sizing)
                .scaled(sizing.isScaled())
                .scaleRatio(sizing.getScaleRatio())
                .build();
    }

    // Build plan with custom per-leg execQty (used after sizing)
    private ExecutionPlanDto buildPlan(List<ResolvedLegDto> resolved, List<BigDecimal> execQtyOverride) {
        ExecutionPlanDto.ExecutionPlanDtoBuilder pb = ExecutionPlanDto.builder();
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedLegDto rl = resolved.get(i);
            BigDecimal execQty = (execQtyOverride != null && i < execQtyOverride.size())
                    ? execQtyOverride.get(i)
                    : rl.getQty(); // fallback to requested qty

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
                            .execQty(execQty) // sized qty
                            .build();
            pb.leg(leg);
        }
        return pb.build();
    }
}
