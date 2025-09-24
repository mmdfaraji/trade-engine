package com.arbitrage.consumer;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.enums.AckAction;
import com.arbitrage.service.api.SignalProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalConsumerService {

  private final Connection connection;
  private final JetStream js;
  private final ObjectMapper mapper;
  private final SignalProcessor processor;

  @Value("${app.nats.subject}")
  private String subject;

  @Value("${app.nats.stream}")
  private String stream;

  @Value("${app.nats.durable}")
  private String durable;

  @Value("${app.nats.fetchBatch:50}")
  private int fetchBatch;

  @Value("${app.nats.fetchWaitMs:1000}")
  private long fetchWaitMs;

  @Value("${app.nats.dlqSubject:}")
  private String dlqSubject;

  private JetStreamSubscription sub;

  @PostConstruct
  public void subscribe() throws Exception {
    PullSubscribeOptions pso =
        PullSubscribeOptions.builder().stream(stream).durable(durable).build();

    this.sub = js.subscribe(subject, pso);
    log.info(
        "JetStream pull subscription is ready: stream={}, durable={}, subject={}",
        stream,
        durable,
        subject);
  }

  @Scheduled(fixedDelay = 300)
  public void poll() {
    if (sub == null) return;
    try {
      List<Message> batch = sub.fetch(fetchBatch, Duration.ofMillis(fetchWaitMs));
      if (batch == null || batch.isEmpty()) return;

      for (Message m : batch) {
        handleMessage(m);
      }
    } catch (Exception e) {
      log.error("Polling error", e);
    }
  }

  private void handleMessage(Message m) {
    final String body = new String(m.getData(), StandardCharsets.UTF_8);
    try {
      // Parse the inbound JSON into our DTOs (case-insensitive per Jackson config)
      SignalMessageDto dto = mapper.readValue(body, SignalMessageDto.class);

      // Process through the business pipeline
      ProcessResult result = processor.process(dto);

      // Log a concise outcome line
      log.info(
          "Signal processed: decision={}, ack={}, externalId={}, signalId={}",
          result.getStatus(),
          result.getAckAction(),
          dto.getSignalId(),
          result.getSignalId().orElse(null));

      // Map to JetStream ack semantic
      applyAck(m, result.getAckAction(), dto);

    } catch (Exception ex) {
      // Parsing or unexpected internal error -> choose policy:
      // We publish to DLQ and TERM to prevent poison-message loop.
      log.error("Fatal processing error, TERM & DLQ. payload={}", body, ex);
      publishToDLQ(body, ex);
      safeTerm(m);
    }
  }

  private void applyAck(Message m, AckAction action, SignalMessageDto dto) {
    try {
      switch (action) {
        case ACK:
          m.ack();
          log.debug("Acked message; externalId={}", dto.getSignalId());
          break;
        case NO_ACK:
          // Do not ack. Let ackWait expire for redelivery.
          log.warn("NoAck (will redeliver after ackWait). externalId={}", dto.getSignalId());
          break;
        case NAK:
          // If you prefer immediate redelivery (be cautious about tight loops)
          m.nak();
          log.warn("Nak issued (immediate redelivery). externalId={}", dto.getSignalId());
          break;
        case TERM:
          m.term();
          log.warn("Terminated message. externalId={}", dto.getSignalId());
          break;
        default:
          break;
      }
    } catch (Exception e) {
      log.error("Ack operation failed ({}). externalId={}", action, dto.getSignalId(), e);
    }
  }

  private void safeTerm(Message m) {
    try {
      m.term();
    } catch (Exception ignore) {
    }
  }

  private void publishToDLQ(String json, Exception e) {
    if (dlqSubject == null || dlqSubject.isBlank()) {
      log.warn("DLQ subject not configured; skipping DLQ publish.");
      return;
    }
    try {
      String payload = "{\"error\":\"" + escape(e.getMessage()) + "\",\"original\":" + json + "}";
      connection.publish(dlqSubject, payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      log.error("DLQ publish failed", ex);
    }
  }

  private String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
