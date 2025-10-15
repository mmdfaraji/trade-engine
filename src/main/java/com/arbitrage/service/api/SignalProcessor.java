package com.arbitrage.service.api;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.signal.TradeSignalDto;

public interface SignalProcessor {
  ProcessResult process(TradeSignalDto dto);
}
