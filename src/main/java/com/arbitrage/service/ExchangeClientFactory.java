package com.arbitrage.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExchangeClientFactory {

  private static final Locale LOCALE = Locale.ROOT;

  private final Map<String, ExchangeMarketClient> clientMap;

  public ExchangeClientFactory(List<ExchangeMarketClient> exchangeClients) {
    this.clientMap = new ConcurrentHashMap<>();
    if (exchangeClients != null) {
      for (ExchangeMarketClient client : exchangeClients) {
        if (client == null) {
          continue;
        }
        String exchangeName = client.getExchangeName();
        if (!StringUtils.hasText(exchangeName)) {
          continue;
        }
        clientMap.put(exchangeName.trim().toUpperCase(LOCALE), client);
      }
    }
  }

  public ExchangeMarketClient getClient(String exchangeName) {
    if (!StringUtils.hasText(exchangeName)) {
      throw new IllegalArgumentException("Exchange name cannot be null or blank");
    }

    ExchangeMarketClient client = clientMap.get(exchangeName.trim().toUpperCase(LOCALE));
    if (client == null) {
      throw new IllegalArgumentException("No exchange client found for exchange: " + exchangeName);
    }
    return client;
  }
}
