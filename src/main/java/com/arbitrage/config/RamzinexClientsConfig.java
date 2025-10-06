package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.service.ExchangeAccessService;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RamzinexClientsConfig {

  private final ExchangeAccessService accessService;

  private final String exchangeName = "RAMZINEX";

  @Bean
  public RestClient ramzinexOpenRestClient(
      @Value("${app.http.timeout.connect:PT2S}") Duration connectTimeout,
      @Value("${app.http.timeout.read:PT4S}") Duration readTimeout) {

    Exchange ex = accessService.requireExchange(exchangeName);

    var http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    var f = new JdkClientHttpRequestFactory(http);
    f.setReadTimeout(readTimeout);
    return RestClient.builder()
        .baseUrl(ex.getPrivateApiUrl())
        .requestFactory(f)
        .defaultHeader("User-Agent", "Trade-Engine")
        .build();
  }
}
