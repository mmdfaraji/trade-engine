package com.arbitrage.service.api;

import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import java.util.Map;
import java.util.UUID;

public interface SignalEventService {
  void recordReceived(UUID signalId, String externalId, Object payload);

  void recordOk(UUID signalId, ValidationPhase phase);

  void recordFailed(
      UUID signalId,
      ValidationPhase phase,
      RejectCode code,
      String message,
      Map<String, Object> details);

  void recordAccepted(UUID signalId);
}
