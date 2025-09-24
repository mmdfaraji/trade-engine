package com.arbitrage.enums;

public enum AckAction {
  /** Processing completed successfully; ack the message. */
  ACK,
  /** Do not ack; let ackWait expire for redelivery (safer than immediate nak). */
  NO_ACK,
  /** Negative ack (immediate redelivery if configured). Use sparingly. */
  NAK,
  /** Permanently terminate this message (park / drop based on consumer config). */
  TERM
}
