package com.arbitrage.service.market;

import com.arbitrage.model.Quote;
import com.arbitrage.service.api.ExchangeMarketClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PriceService {

  private static final Locale LOCALE = Locale.ROOT;

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
  }
}
