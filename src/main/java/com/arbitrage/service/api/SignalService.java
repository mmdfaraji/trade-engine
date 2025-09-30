package com.arbitrage.service.api;

import com.arbitrage.dto.SignalMessageDto;
import java.util.UUID;

public interface SignalService {
  UUID saveSignal(SignalMessageDto dto);

  void updateStatus(Long signalId, String newStatus);
}
