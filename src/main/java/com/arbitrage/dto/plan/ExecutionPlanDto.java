package com.arbitrage.dto.plan;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** A collection of execution legs computed during validation/reservation phases. */
@Value
@Builder
public class ExecutionPlanDto {
  @Singular List<ExecutionLegPlanDto> legs;
}
