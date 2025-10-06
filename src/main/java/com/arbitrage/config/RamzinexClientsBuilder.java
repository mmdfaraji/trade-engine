package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RamzinexClientsBuilder {

  private final ExchangeClientFactory baseFactory;
  private final RamzinexTokenProvider tokenService;

  public RamzinexClientsBuilder(
      ExchangeClientFactory baseFactory, RamzinexTokenProvider tokenService) {
    this.baseFactory = baseFactory;
    this.tokenService = tokenService;
  }

  public RestClient buildPublic(Exchange ex) {
    return baseFactory.buildPublic(ex);
  }

  public RestClient buildPrivate(Exchange ex, ExchangeAccount acc) {
    return baseFactory
        .privateBuilder(ex, acc)
        .defaultHeader("x-api-key", acc.getApiKey())
        .requestInterceptor(
            (request, body, exec) -> {
              String token = tokenService.getToken(ex.getName());
              request.getHeaders().set("Authorization2", "Bearer " + token);
              return exec.execute(request, body);
            })
        .build();
  }
}
