package com.arbitrage.config;

import io.nats.client.*;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NatsConfig {

  @Value("${app.nats.url}")
  private String natsUrl;

  @Bean(destroyMethod = "close")
  public Connection natsConnection() throws Exception {
    Options options =
        new Options.Builder()
            .server(natsUrl)
            .connectionName("signals-service")
            .connectionTimeout(Duration.ofSeconds(5))
            .reconnectWait(Duration.ofSeconds(1))
            .maxReconnects(-1)
            .pingInterval(Duration.ofSeconds(15))
            .maxPingsOut(2)
            .errorListener(
                new ErrorListener() {
                  @Override
                  public void exceptionOccurred(Connection conn, Exception exp) {
                    log.error("NATS error", exp);
                  }

                  @Override
                  public void errorOccurred(Connection conn, String type) {
                    log.warn("NATS errorOccurred: {}", type);
                  }

                  @Override
                  public void slowConsumerDetected(Connection conn, Consumer consumer) {
                    log.warn("Slow consumer detected");
                  }
                })
            .build();

    Connection connection = Nats.connect(options);
    log.info("Connected to NATS at {}", natsUrl);
    return connection;
  }

  @Bean
  public JetStream jetStream(Connection connection) throws Exception {
    return connection.jetStream(JetStreamOptions.DEFAULT_JS_OPTIONS);
  }

  @Bean
  public JetStreamManagement jetStreamManagement(Connection connection) throws Exception {
    return connection.jetStreamManagement();
  }
}
