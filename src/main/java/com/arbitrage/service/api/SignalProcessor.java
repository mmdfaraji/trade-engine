package com.arbitrage.service.api;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.processor.ProcessResult;

public interface SignalProcessor {
  ProcessResult process(SignalMessageDto dto);
}
