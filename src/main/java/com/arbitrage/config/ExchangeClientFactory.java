package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangeClientFactory {

  public RestClient buildPublic(Exchange exchange) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
    rf.setReadTimeout(Duration.ofSeconds(6));

    return RestClient.builder()
        .baseUrl(Objects.requireNonNull(exchange.getPublicApiUrl(), "publicApiUrl is null"))
        .requestFactory(rf)
        .defaultHeader("User-Agent", "Trade-Engine")
        .build();
  }

  public RestClient.Builder buildPrivate(Exchange exchange) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
    rf.setReadTimeout(Duration.ofSeconds(8));

    return RestClient.builder()
        .baseUrl(exchange.getPrivateApiUrl())
        .requestFactory(rf)
        .defaultHeader("User-Agent", "Trade-Engine");
  }

  public RestClient buildPublic(String privateApiUrl) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
    rf.setReadTimeout(Duration.ofSeconds(6));

    return RestClient.builder()
        .baseUrl(privateApiUrl)
        .requestFactory(rf)
        .defaultHeader("User-Agent", "Trade-Engine")
        .build();
  }
}
