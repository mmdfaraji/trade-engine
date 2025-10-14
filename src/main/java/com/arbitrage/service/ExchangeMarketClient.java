package com.arbitrage.service;

import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import java.math.BigDecimal;
import java.util.List;

public interface ExchangeMarketClient {

  String getExchangeName();

  BigDecimal getWalletBalance(String currency);

  List<Quote> getQuotes();

  OrderAck submitOrder(OrderRequest orderRequest);

  boolean cancelOrder(String orderId);
}
