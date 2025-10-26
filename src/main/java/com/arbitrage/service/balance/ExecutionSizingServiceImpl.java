package com.arbitrage.service.balance;

import com.arbitrage.dto.balance.BucketKeyDto;
import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.balance.SizingResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure sizing logic: compute a single alpha in [0,1] using bucket availabilities,
 * then apply it to all legs' requested base quantities.
 * Rounding is DOWN to avoid overspend.
 */
@Slf4j
@Service
public class ExecutionSizingServiceImpl implements ExecutionSizingService {

    private static final RoundingMode RM = RoundingMode.DOWN;
    private static final MathContext MC = new MathContext(34, RM); // high precision
    private static final int QTY_SCALE = 18; // safe internal scale before market quantization

    @Override
    public SizingResultDto sizeForBalances(
            List<ResolvedLegDto> legs,
            Map<BucketKeyDto, BigDecimal> availableByBucket) {

        if (legs == null || legs.isEmpty()) {
            return SizingResultDto.builder()
                    .scaled(false)
                    .scaleRatio(BigDecimal.ZERO)
                    .execQty(List.of())
                    .build();
        }

        // 1) Aggregate required spend per bucket (BUY: quote, SELL: base units)
        Map<BucketKeyDto, BigDecimal> requiredByBucket = new HashMap<>();
        for (ResolvedLegDto rl : legs) {
            BucketKeyDto key = new BucketKeyDto(rl.getExchangeAccountId(), rl.getSpendCurrencyId());
            BigDecimal prev = requiredByBucket.get(key);
            BigDecimal add = rl.getRequiredSpend();
            requiredByBucket.put(key, prev == null ? add : prev.add(add, MC));
        }

        // 2) Compute alpha = min over buckets of (available / required), capped in [0,1]
        BigDecimal alpha = BigDecimal.ONE;
        for (Map.Entry<BucketKeyDto, BigDecimal> e : requiredByBucket.entrySet()) {
            BigDecimal required = e.getValue();
            if (required == null || required.signum() <= 0) {
                continue;
            }
            BigDecimal available = availableByBucket.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal ratio = (available.signum() <= 0)
                    ? BigDecimal.ZERO
                    : available.divide(required, MC);
            if (ratio.compareTo(alpha) < 0) {
                alpha = ratio;
            }
            if (alpha.signum() == 0) break;
        }

        // 3) Clamp alpha to [0,1]
        if (alpha.compareTo(BigDecimal.ONE) > 0) alpha = BigDecimal.ONE;
        if (alpha.signum() < 0) alpha = BigDecimal.ZERO;

        // 4) Compute per-leg execQty = reqQty * alpha (rounded down)
        List<BigDecimal> execQty = new ArrayList<>(legs.size());
        for (ResolvedLegDto rl : legs) {
            BigDecimal qty = rl.getQty();
            BigDecimal sized = (qty == null || qty.signum() <= 0)
                    ? BigDecimal.ZERO
                    : qty.multiply(alpha, MC).setScale(QTY_SCALE, RM);
            execQty.add(sized);
        }

        boolean scaled = alpha.compareTo(BigDecimal.ONE) < 0;
        log.debug("sizing computed: alpha={}, scaled={}, legs={}", alpha, scaled, legs.size());

        return SizingResultDto.builder()
                .scaled(scaled)
                .scaleRatio(alpha)
                .execQty(execQty)
                .build();
    }
}