package com.arbitrage.dto.plan;

import com.arbitrage.dto.processor.StepResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceReservationResultDto {
  private final StepResult result;
  private final ExecutionPlanDto plan;
}
