package com.arbitrage.service.market;

import org.springframework.stereotype.Service;

@Service
public class PriceService {

  /*  private static final Locale LOCALE = Locale.ROOT;

  private final Map<String, ExchangeMarketClient> clientsByExchange;

  public PriceService(List<ExchangeMarketClient> exchangeClients) {
    Objects.requireNonNull(exchangeClients, "exchangeClients");
    this.clientsByExchange =
        exchangeClients.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    c -> c.getExchangeName().toUpperCase(LOCALE),
                    Function.identity(),
                    (existing, replacement) -> replacement));
  }

  public List<Quote> quotesFor(String exchangeName) {
    if (!StringUtils.hasText(exchangeName)) {
      throw new IllegalArgumentException("exchangeName must not be blank");
    }
    ExchangeMarketClient client = clientsByExchange.get(exchangeName.toUpperCase(LOCALE));
    if (client == null) {
      throw new IllegalArgumentException("No market client registered for " + exchangeName);
    }
    return client.getQuotes();
  }*/
}
