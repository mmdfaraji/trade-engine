package com.arbitrage.service.validators.market;

import com.arbitrage.dto.market.MarketValidationReportDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.SignalContext;

public interface MarketRulesService {
  MarketValidationReportDto applyAll(SignalContext ctx, ExecutionPlanDto plan);

  MarketValidationReportDto applyPriceGuards(SignalContext ctx, ExecutionPlanDto plan);
}
