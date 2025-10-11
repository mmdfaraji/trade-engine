package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NobitexClients {

  private final ExchangeClientFactory factory;

  public NobitexClients(ExchangeClientFactory factory) {
    this.factory = factory;
  }

  public RestClient publicClient(Exchange ex) {
    return factory.buildPublicClient(ex);
  }

  public RestClient privateClient(Exchange ex, ExchangeAccount acc) {
    String token = Objects.requireNonNull(acc.getApiKey(), "Nobitex token (api_key) is null");
    return factory
        .buildPrivateClient(ex)
        .requestInterceptor(
            (req, body, exec) -> {
              req.getHeaders().set("Authorization", "Token " + token);
              return exec.execute(req, body);
            })
        .build();
  }
}
