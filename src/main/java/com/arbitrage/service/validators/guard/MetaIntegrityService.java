package com.arbitrage.service.validators.guard;

import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.MetaDto;

public interface MetaIntegrityService {
  StepResult validate(MetaDto meta);
}
