package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RamzinexClients {

  private final ExchangeClientFactory factory;
  private final RamzinexTokenProvider tokenCache;

  public RamzinexClients(ExchangeClientFactory factory, RamzinexTokenProvider tokenCache) {
    this.factory = factory;
    this.tokenCache = tokenCache;
  }

  public RestClient publicClient(Exchange ex) {
    return factory.buildPublicClient(ex);
  }

  public RestClient privateClient(Exchange ex, ExchangeAccount acc) {
    return factory
        .buildPrivateClient(ex)
        .defaultHeader("x-api-key", acc.getSecretKey())
        .requestInterceptor(
            (req, body, exec) -> {
              String token = tokenCache.token(ex.getName(), acc.getLabel());
              req.getHeaders().set("Authorization2", "Bearer " + token);
              return exec.execute(req, body);
            })
        .build();
  }

  public RestClient openRestClient(Exchange ex) {
    return factory
        .buildPrivateClient(ex)
        .requestInterceptor((req, body, exec) -> exec.execute(req, body))
        .build();
  }
}
