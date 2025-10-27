package com.arbitrage.dto.market;

import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.StepResult;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketValidationReportDto {
  private final StepResult result;
  private final ExecutionPlanDto plan;

  // optional diagnostics (per-leg, aligned with plan.legs order)
  private final List<BigDecimal> priceBefore;
  private final List<BigDecimal> priceAfter;
  private final List<BigDecimal> tickApplied; // tickSize used per leg (null if none)
  private final BigDecimal slippageBpsApplied; // if any global slippage applied (null otherwise)
}
