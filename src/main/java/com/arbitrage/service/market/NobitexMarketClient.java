package com.arbitrage.service.market;

import com.arbitrage.config.NobitexClients;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.ExchangeAccessService;
import com.arbitrage.service.api.ExchangeMarketClient;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NobitexMarketClient implements ExchangeMarketClient {

  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final ExchangeAccessService exchangeAccessService;
  private final NobitexClients clients;

  private RestClient pub;
  private RestClient prv;

  private final String EXCHANGE = "NOBITEX";
  private final String ACCOUNT = "Nobitex";

  public NobitexMarketClient(
      CurrencyExchangeRepository currencyExchangeRepository,
      ExchangeAccessService exchangeAccessService,
      NobitexClients clients) {
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.exchangeAccessService = exchangeAccessService;
    this.clients = clients;
  }

  @PostConstruct
  void init() {
    Exchange ex = exchangeAccessService.requireExchange(EXCHANGE);
    ExchangeAccount acc = exchangeAccessService.requireAccount(EXCHANGE, ACCOUNT);
    this.pub = clients.publicClient(ex);
    this.prv = clients.privateClient(ex, acc);
  }

  @Override
  public BigDecimal getWalletBalance(String currency) {
    String c = currency.toLowerCase(Locale.ROOT);
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("currency", c);
      var resp =
          prv.post()
              .uri("/users/wallets/balance")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(java.util.Map.class);
      Object bal = resp != null ? resp.get("balance") : null;
      if (bal == null) throw new IllegalStateException("Empty balance");
      return new BigDecimal(String.valueOf(bal));
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public List<Quote> getQuotes() {
    var entries = currencyExchangeRepository.findByExchange_Name(EXCHANGE);
    if (entries == null || entries.isEmpty()) return List.of();

    long ts = Instant.now().toEpochMilli();
    List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      String symbol = toSymbol(cx);
      String[] pp = symbol.split("-");
      if (pp.length != 2) continue;
      String base = pp[0], quote = pp[1];

      BigDecimal ask = bestPrice(base, quote, "sell");
      BigDecimal bid = bestPrice(base, quote, "buy");
      if (ask != null && bid != null) out.add(new Quote(symbol, bid, ask, ts));
    }
    return out;
  }

  private BigDecimal bestPrice(String base, String quote, String side) {
    try {
      var resp =
          pub.get()
              .uri(
                  b ->
                      b.path("/market/stats")
                          .queryParam("srcCurrency", base.toLowerCase())
                          .queryParam("dstCurrency", quote.toLowerCase())
                          .build())
              .retrieve()
              .body(java.util.Map.class);
      if (resp == null || resp.get("stats") == null) return null;
      java.util.Map<?, ?> stats = (java.util.Map<?, ?>) resp.get("stats");
      String key = (base + "-" + quote).toLowerCase(Locale.ROOT);
      Object entry = stats.get(key);
      if (!(entry instanceof java.util.Map)) {
        for (var e : stats.entrySet()) {
          if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) {
            entry = e.getValue();
            break;
          }
        }
      }
      if (!(entry instanceof java.util.Map)) return null;
      java.util.Map<?, ?> s = (java.util.Map<?, ?>) entry;
      Object price = "sell".equals(side) ? s.get("bestSell") : s.get("bestBuy");
      return price == null ? null : new BigDecimal(String.valueOf(price));
    } catch (RestClientResponseException http) {
      return null;
    }
  }

  @Override
  public OrderAck submitOrder(OrderRequest r) {
    try {
      String pair = r.getSymbol().toLowerCase(Locale.ROOT);
      String[] parts = pair.split("[-_]");
      if (parts.length != 2) throw new IllegalArgumentException("symbol must be like 'btc-usdt'");
      String src = parts[0], dst = parts[1];

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("type", r.getSide().toLowerCase(Locale.ROOT));
      form.add("execution", "limit");
      form.add("srcCurrency", src);
      form.add("dstCurrency", dst);
      form.add("amount", r.getQty().toPlainString());
      form.add("price", r.getPrice().toPlainString());
      form.add("clientOrderId", defaultClientOrderId(r));

      var resp =
          prv.post()
              .uri("/market/orders/add")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(java.util.Map.class);

      String status =
          resp != null ? String.valueOf(resp.getOrDefault("status", "unknown")) : "unknown";
      String orderId = resp != null ? String.valueOf(resp.get("orderId")) : null;
      return new OrderAck(form.getFirst("clientOrderId"), orderId, status);
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    try {
      var form = new LinkedMultiValueMap<String, String>();
      form.add("status", "canceled");
      if (orderId != null && orderId.matches("\\d+")) form.add("order", orderId);
      else form.add("clientOrderId", orderId);

      var resp =
          prv.post()
              .uri("/market/orders/update-status")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(java.util.Map.class);

      String status = resp != null ? String.valueOf(resp.get("status")) : null;
      String updated = resp != null ? String.valueOf(resp.get("updatedStatus")) : null;
      return "ok".equalsIgnoreCase(status)
          || "canceled".equalsIgnoreCase(status)
          || "Canceled".equalsIgnoreCase(updated);
    } catch (RestClientResponseException http) {
      return false;
    }
  }

  private static String defaultClientOrderId(OrderRequest req) {
    return ("CLI-" + req.getSymbol() + "-" + System.currentTimeMillis()).toUpperCase(Locale.ROOT);
  }

  private static String toSymbol(CurrencyExchange cx) {
    String exSym = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSym)) return exSym.toLowerCase(Locale.ROOT);
    String base = cx.getCurrency().getSymbol().toLowerCase(Locale.ROOT);
    String quote = "usdt";
    return base + "-" + quote;
  }
}
