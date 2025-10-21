package com.arbitrage.service.api;

import com.arbitrage.dto.balance.ResolvedLegDto;
import com.arbitrage.dto.signal.OrderInstructionDto;
import java.util.List;
import java.util.UUID;

public interface SignalLegService {
  void persistResolved(
      UUID signalId, List<ResolvedLegDto> resolved, List<OrderInstructionDto> originalOrders);
}
