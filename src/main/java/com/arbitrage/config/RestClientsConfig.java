package com.arbitrage.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientsConfig {

  private JdkClientHttpRequestFactory jdkFactory(Duration connect, Duration read) {
    var httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(connect).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(read);

    return requestFactory;
  }

  @Bean
  public RestClient nobitexPublicRestClient(
      @Value("${app.exchanges.nobitex.base-url:https://apiv2.nobitex.ir}") String baseUrl,
      @Value("${app.http.timeout.connect:2s}") Duration connectTimeout,
      @Value("${app.http.timeout.read:5s}") Duration readTimeout) {

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(jdkFactory(connectTimeout, readTimeout))
        .defaultHeader("User-Agent", "Trade-Engine")
        .build();
  }

  @Bean
  public RestClient nobitexPrivateRestClient(
      @Value("${app.exchanges.nobitex.base-url:https://apiv2.nobitex.ir}") String baseUrl,
      @Value("${app.exchanges.nobitex.token:}") String token,
      @Value("${app.http.timeout.connect:2s}") Duration connectTimeout,
      @Value("${app.http.timeout.read:5s}") Duration readTimeout) {

    var b =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(jdkFactory(connectTimeout, readTimeout))
            .defaultHeader("User-Agent", "Trade-Engine");
    if (token != null && !token.isBlank()) {
      b = b.defaultHeader("Authorization", "Token " + token.trim());
    }

    return b.build();
  }
}
