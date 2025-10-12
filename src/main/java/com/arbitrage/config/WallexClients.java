package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WallexClients {

  private final ExchangeClientFactory factory;

  public WallexClients(ExchangeClientFactory factory) {
    this.factory = factory;
  }

  public RestClient client(Exchange ex, ExchangeAccount acc) {
    return factory
        .buildPrivateClient(ex)
        .requestInterceptor(
            (req, body, exec) -> {
              req.getHeaders().set("x-api-key", acc.getApiKey());
              return exec.execute(req, body);
            })
        .build();
  }
}
