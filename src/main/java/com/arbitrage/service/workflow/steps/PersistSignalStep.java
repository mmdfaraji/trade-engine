package com.arbitrage.service.workflow.steps;

import com.arbitrage.dto.processor.PersistSignalResult;
import com.arbitrage.dto.signal.TradeSignalDto;

public interface PersistSignalStep {
  PersistSignalResult execute(TradeSignalDto dto);
}
