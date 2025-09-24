package com.arbitrage.config;

import io.nats.client.*;
import io.nats.client.api.*;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NatsBootstrap {
  private final JetStreamManagement jsm;

  @Value("${app.nats.stream}")
  private String streamName;

  @Value("${app.nats.subject}")
  private String subject;

  @Value("${app.nats.durable}")
  private String durable;

  @Value("${app.nats.ackWaitSeconds:30}")
  private int ackWaitSeconds;

  @Value("${app.nats.maxDeliver:5}")
  private int maxDeliver;

  @PostConstruct
  public void ensureStreamAndConsumer() {
    try {
      // --- Ensure stream ---
      StreamConfiguration sc =
          StreamConfiguration.builder()
              .name(streamName)
              .storageType(StorageType.File)
              .retentionPolicy(RetentionPolicy.Limits)
              .subjects(subject)
              .build();
      try {
        jsm.addStream(sc);
        log.info("Created stream '{}'", streamName);
      } catch (JetStreamApiException e) {
        if (e.getErrorCode() == 400 || e.getErrorCode() == 409) {
          jsm.updateStream(sc);
          log.info("Updated stream '{}'", streamName);
        } else throw e;
      }

      // --- Ensure durable consumer ---
      ConsumerConfiguration cc =
          ConsumerConfiguration.builder()
              .durable(durable)
              .ackPolicy(AckPolicy.Explicit)
              .ackWait(Duration.ofSeconds(ackWaitSeconds))
              .maxDeliver(maxDeliver)
              .filterSubject(subject)
              // .backoff(...) // enable if server supports it and you need it
              .deliverPolicy(DeliverPolicy.All)
              .build();

      jsm.addOrUpdateConsumer(streamName, cc);
      log.info(
          "Ensured consumer '{}' on stream '{}': ackWait={}s, maxDeliver={}",
          durable,
          streamName,
          ackWaitSeconds,
          maxDeliver);
    } catch (Exception e) {
      log.error("NATS bootstrap failed", e);
      throw new RuntimeException(e);
    }
  }
}
