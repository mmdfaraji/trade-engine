package com.arbitrage.service.validators.api;

import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;

public interface FreshnessValidator {
    StepResult validate(SignalContext ctx);
}
