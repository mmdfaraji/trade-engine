package com.arbitrage.service.market;

import com.arbitrage.model.Quote;
import com.arbitrage.service.api.ExchangeMarketClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriceService {

  private final ExchangeMarketClient nobitexMarketClient;

  public List<Quote> quotesFor(String exchangeName) {
    return null; // nobitexMarketClient.getQuotesByExchangeName(exchangeName);
  }
}
