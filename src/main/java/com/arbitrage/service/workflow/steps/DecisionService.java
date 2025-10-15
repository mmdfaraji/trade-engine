package com.arbitrage.service.workflow.steps;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.ValidationPhase;
import java.util.Optional;

public interface DecisionService {
  Optional<ProcessResult> handle(SignalContext ctx, StepResult stepResult, ValidationPhase phase);
}
