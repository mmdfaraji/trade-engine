package com.arbitrage.service.api;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.enums.SignalStatus;
import java.util.UUID;

public interface SignalService {
  UUID saveSignal(SignalMessageDto dto);

  void updateStatus(UUID signalId, SignalStatus newStatus);
}
