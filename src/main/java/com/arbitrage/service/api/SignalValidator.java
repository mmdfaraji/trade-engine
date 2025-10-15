package com.arbitrage.service.api;

import com.arbitrage.dto.balance.BalanceValidationReportDto;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;

/**
 * Unified validation facade for the pipeline phases. This interface is intentionally pure (no side
 * effects).
 *
 * <p>Phase-1: Freshness / latency validation Phase-2: Balance sufficiency (read-only; reservation
 * is a separate side-effect service)
 *
 * <p>Phases 3+ (market rules, liquidity, pnl, risk) can be added here later.
 */
public interface SignalValidator {

  StepResult validateIntegrity(SignalContext ctx);

  /**
   * Phase-1: Validate signal freshness/latency using ctx dto.createdAt and dto.ttlMs/maxLatencyMs.
   *
   * @return StepResult.ok() if within bounds; otherwise StepResult.fail(...) with rejection
   *     details.
   */
  StepResult validateFreshness(SignalContext ctx);

  /**
   * Phase-2: Validate balance sufficiency across required buckets (read-only). Should resolve
   * exchanges/pairs/accounts and compute required amounts.
   *
   * @return A report with StepResult and a preliminary execution plan if ok.
   */
  BalanceValidationReportDto validateBalance(SignalContext ctx);

  // Future phases:
  // StepResult validateMarketRules(SignalContext ctx, ExecutionPlanDto plan);
  // StepResult validateLiquidity(SignalContext ctx, ExecutionPlanDto plan);
  // StepResult validatePnl(SignalContext ctx, ExecutionPlanDto plan);
  // StepResult validateRisk(SignalContext ctx, ExecutionPlanDto plan);
}
