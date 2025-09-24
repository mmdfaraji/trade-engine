package com.arbitrage.consumer;

import com.arbitrage.dto.SignalMessageDto;
import com.arbitrage.service.api.SignalService;
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
  private JetStreamSubscription sub;

  private final SignalService signalService;

  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${app.nats.subject}")
  private String subject;

  @Value("${app.nats.stream}")
  private String stream;

  @Value("${app.nats.durable}")
  private String durable;

  @Value("${app.nats.dlqSubject}")
  private String dlqSubject;

  @Value("${app.nats.fetchBatch:50}")
  private int fetchBatch;

  @Value("${app.nats.fetchWaitMs:1000}")
  private long fetchWaitMs;

  @PostConstruct
  public void subscribe() throws Exception {
    // Durable pull subscription
    PullSubscribeOptions pso =
        PullSubscribeOptions.builder().stream(stream).durable(durable).build();

    this.sub = js.subscribe(subject, pso);
    log.info(
        "JetStream pull subscription created: stream={}, durable={}, subject={}",
        stream,
        durable,
        subject);
  }

  /**
   * Poll in batches (fixed delay). You can also use a separate executor. If throughput is high,
   * increase fetchBatch / reduce delay / parallelize processing.
   */
  @Scheduled(fixedDelay = 300)
  public void poll() {
    if (sub == null) return;

    try {
      List<Message> batch = sub.fetch(fetchBatch, Duration.ofMillis(fetchWaitMs));
      if (batch == null || batch.isEmpty()) {
        return;
      }
      for (Message m : batch) {
        processAndAck(m);
      }
    } catch (Exception e) {
      log.error("Polling error", e);
    }
  }

  private void processAndAck(Message m) {
    String json = new String(m.getData(), StandardCharsets.UTF_8);
    try {
      SignalMessageDto dto = mapper.readValue(json, SignalMessageDto.class);
      Long id = signalService.saveSignal(dto);
      m.ack(); // ack only on success
      log.info("Processed signal id={} seq={}", id, metaSeq(m));
    } catch (Exception e) {
      log.error(
          "Process failed, will not ack (message will be redelivered): {}", e.getMessage(), e);
      publishToDLQ(json, e);
      // No ack -> redelivery (consider term / max-deliver in consumer config if needed)
    }
  }

  private String metaSeq(Message m) {
    try {
      return m.metaData() != null ? String.valueOf(m.metaData().streamSequence()) : "n/a";
    } catch (Exception ignore) {
      return "n/a";
    }
  }

  private void publishToDLQ(String json, Exception e) {
    if (dlqSubject == null || dlqSubject.isBlank()) return;
    try {
      String payload = "{\"error\":\"" + esc(e.getMessage()) + "\",\"original\":" + json + "}";
      connection.publish(dlqSubject, payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      log.error("DLQ publish failed: {}", ex.getMessage(), ex);
    }
  }

  private String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
