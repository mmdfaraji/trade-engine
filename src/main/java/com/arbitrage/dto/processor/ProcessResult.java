package com.arbitrage.dto.processor;

import com.arbitrage.enums.AckAction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProcessResult {
  public enum Status {
    ACCEPTED,
    REJECTED,
    RETRY
  }

  private final Status status;
  private final AckAction ackAction; // <--- NEW: explicit ack mapping
  private final UUID signalId;
  private final List<Rejection> rejections;

  // Optional metadata for logs/metrics
  private final Map<String, Object> meta;

  public static ProcessResult accepted(UUID id) {
    return ProcessResult.builder()
        .status(Status.ACCEPTED)
        .ackAction(AckAction.ACK)
        .signalId(id)
        .rejections(List.of())
        .build();
  }

  public static ProcessResult rejected(UUID id, List<Rejection> reasons) {
    return ProcessResult.builder()
        .status(Status.REJECTED)
        .ackAction(AckAction.ACK) // logical failure -> ack
        .signalId(id)
        .rejections(reasons)
        .build();
  }

  public static ProcessResult retryTransient(List<Rejection> reasons) {
    return ProcessResult.builder()
        .status(Status.RETRY)
        .ackAction(AckAction.NO_ACK) // let ackWait trigger redelivery
        .signalId(null)
        .rejections(reasons)
        .build();
  }

  public boolean isAck() {
    return ackAction == AckAction.ACK;
  }
}
