package com.arbitrage.service.api;

import com.arbitrage.dto.SignalMessageDto;

public interface SignalService {
  Long saveSignal(SignalMessageDto dto);
}
