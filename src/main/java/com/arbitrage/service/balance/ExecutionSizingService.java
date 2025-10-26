package com.arbitrage.service.balance;

import com.arbitrage.dto.balance.BucketKeyDto;
import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.balance.SizingResultDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ExecutionSizingService {

    /**
     * Compute a global scale ratio (alpha in [0,1]) based on available balances per spend-bucket,
     * then produce per-leg execQty = reqQty * alpha (rounded down).
     * - If any spend bucket is missing, it's treated as 0 available.
     * - If alpha <= 0, caller should treat it as "no executable size".
     */
    SizingResultDto sizeForBalances(List<ResolvedLegDto> legs, Map<BucketKeyDto, BigDecimal> availableByBucket);
}
