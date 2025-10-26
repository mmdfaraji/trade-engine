package com.arbitrage.dto.balance;

import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.StepResult;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Output of validateBalance (read-only). Contains high-level StepResult, an execution plan (if ok),
 * and per-bucket required amounts.
 */
@Getter
@Builder
public class BalanceValidationReportDto {
  private final StepResult result;
  private final ExecutionPlanDto plan;
  private final Map<BucketKeyDto, BigDecimal> bucketAmounts;

  // sizing diagnostics
  private final boolean scaled;          // true if alpha < 1
  private final BigDecimal scaleRatio;   // alpha
}
