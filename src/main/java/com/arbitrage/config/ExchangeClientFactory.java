package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangeClientFactory {

  public RestClient buildPublic(Exchange ex) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
    f.setReadTimeout(Duration.ofSeconds(6));

    return RestClient.builder()
        .baseUrl(ex.getPublicApiUrl())
        .requestFactory(f)
        .defaultHeader("User-Agent", "Trade-Engine")
        .build();
  }

  public RestClient.Builder privateBuilder(Exchange ex, ExchangeAccount acc) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
    f.setReadTimeout(Duration.ofSeconds(8));

    return RestClient.builder()
        .baseUrl(ex.getPrivateApiUrl())
        .requestFactory(f)
        .defaultHeader("User-Agent", "Trade-Engine");
  }
}
