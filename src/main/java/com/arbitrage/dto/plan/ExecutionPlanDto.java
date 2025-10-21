package com.arbitrage.dto.plan;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** A collection of execution legs computed during validation/reservation phases. */
@Getter
@Builder
public class ExecutionPlanDto {
  @Singular("leg")
  private final List<ExecutionLegPlanDto> legs;
}
