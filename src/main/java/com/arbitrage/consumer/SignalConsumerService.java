package com.arbitrage.consumer;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.signal.MetaDto;
import com.arbitrage.dto.signal.TradeSignalDto;
import com.arbitrage.enums.AckAction;
import com.arbitrage.service.api.SignalProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

  private final io.nats.client.Connection connection;
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
      // Deserialize inbound JSON directly to TradeSignalDto
      TradeSignalDto dto = mapper.readValue(body, TradeSignalDto.class);

      // Backfill meta.createdAt from NATS metadata if missing
      Instant createdAtFromNats = null;
      if (m.metaData() != null && m.metaData().timestamp() != null) {
        createdAtFromNats = m.metaData().timestamp().toInstant();
      }
      if (dto.getMeta() == null) {
        dto.setMeta(new MetaDto());
      }
      if (dto.getMeta().getCreatedAt() == null && createdAtFromNats != null) {
        dto.getMeta().setCreatedAt(createdAtFromNats);
      }

      // Log concise inbound summary
      String sigId = dto.getMeta() != null ? dto.getMeta().getSignalId().toString() : "n/a";
      String pair = dto.getMeta() != null ? dto.getMeta().getPair() : "n/a";
      int legs = dto.getOrders() != null ? dto.getOrders().size() : 0;
      log.info("NATS message received: signalId={}, pair={}, orders={}", sigId, pair, legs);

      // Process through the business pipeline
      ProcessResult result = processor.process(dto);

      // Outcome logging
      log.info(
          "Signal processed: decision={}, ack={}, signalId={}, savedId={}",
          result.getStatus(),
          result.getAckAction(),
          sigId,
          result.getSignalId());

      // Ack mapping
      applyAck(m, result.getAckAction(), sigId);

    } catch (Exception ex) {
      // Fatal parsing or unexpected error → send to DLQ and TERM
      log.error("Fatal processing error, TERM & DLQ. payload={}", body, ex);
      publishToDLQ(body, ex);
      safeTerm(m);
    }
  }

  private void applyAck(Message m, AckAction action, String signalIdForLog) {
    try {
      switch (action) {
        case ACK:
          m.ack();
          log.debug("Acked message; signalId={}", signalIdForLog);
          break;
        case NO_ACK:
          log.warn("NoAck (will redeliver after ackWait). signalId={}", signalIdForLog);
          break;
        case NAK:
          m.nak();
          log.warn("Nak issued (immediate redelivery). signalId={}", signalIdForLog);
          break;
        case TERM:
          m.term();
          log.warn("Terminated message. signalId={}", signalIdForLog);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      log.error("Ack operation failed ({}). signalId={}", action, signalIdForLog, e);
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
