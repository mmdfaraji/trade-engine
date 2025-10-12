package com.arbitrage.dto.plan;

import com.arbitrage.dto.processor.StepResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Result of reservation step (side-effect). If result.isOk() == false, plan may be null. */
@Getter
@AllArgsConstructor
public class BalanceReservationResultDto {
  private final StepResult result;
  private final ExecutionPlanDto plan;
}
