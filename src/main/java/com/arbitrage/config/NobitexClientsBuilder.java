package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NobitexClientsBuilder {

  private final ExchangeClientFactory baseFactory;

  public NobitexClientsBuilder(ExchangeClientFactory baseFactory) {
    this.baseFactory = baseFactory;
  }

  public RestClient buildPublic(Exchange ex) {
    return baseFactory.buildPublic(ex);
  }

  public RestClient buildPrivate(Exchange ex, ExchangeAccount acc) {
    String token =
        Objects.requireNonNull(
            acc.getApiKey(), "Nobitex token (secret) is null on ExchangeAccount");

    return baseFactory
        .privateBuilder(ex, acc)
        .requestInterceptor(
            (request, body, exec) -> {
              request.getHeaders().set("Authorization", "Token " + token);
              return exec.execute(request, body);
            })
        .build();
  }
}
