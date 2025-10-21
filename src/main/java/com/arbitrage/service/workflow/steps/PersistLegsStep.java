package com.arbitrage.service.workflow.steps;

import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;

public interface PersistLegsStep {
  StepResult execute(SignalContext ctx);
}
