package com.arbitrage.service.validators.guard;

import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.dto.signal.OrderInstructionDto;
import java.util.List;

public interface OrdersIntegrityService {
  StepResult validateAndResolve(
      List<OrderInstructionDto> orders, String signalIdForLog, SignalContext ctx);
}
