package com.arbitrage.service.api;

import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.enums.SignalStatus;
import java.util.UUID;

public interface SignalService {
  UUID saveSignal(TradeSignalDto dto);

  void updateStatus(UUID signalId, SignalStatus newStatus);
}
