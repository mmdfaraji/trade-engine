package com.arbitrage.service.api;

import com.arbitrage.dto.SignalMessageDto;

public interface SignalService {
  Long saveSignal(SignalMessageDto dto);

  void updateStatus(Long signalId, String newStatus);
}
