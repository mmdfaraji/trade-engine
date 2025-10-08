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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NobitexMarketClient implements ExchangeMarketClient {

  private static final Locale LOCALE = Locale.ROOT;

  private static final String EXCHANGE = "NOBITEX";
  private static final String ACCOUNT = "Nobitex";

  private static final String PATH_WALLET_BALANCE = "/users/wallets/balance";
  private static final String PATH_STATS = "/market/stats";
  private static final String PATH_ORDER_ADD = "/market/orders/add";
  private static final String PATH_ORDER_UPDATE_STATUS = "/market/orders/update-status";

  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final ExchangeAccessService exchangeAccessService;
  private final NobitexClients nobitexClients;

  private RestClient publicClient;
  private RestClient privateClient;

  public NobitexMarketClient(
      CurrencyExchangeRepository currencyExchangeRepository,
      ExchangeAccessService exchangeAccessService,
      NobitexClients clients) {
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.exchangeAccessService = exchangeAccessService;
    this.nobitexClients = clients;
  }

  @PostConstruct
  void init() {
    Exchange ex = exchangeAccessService.requireExchange(EXCHANGE);
    ExchangeAccount acc = exchangeAccessService.requireAccount(EXCHANGE, ACCOUNT);
    this.publicClient = nobitexClients.publicClient(ex);
    this.privateClient = nobitexClients.privateClient(ex, acc);
  }

  @Override
  public String getExchangeName() {
    return EXCHANGE;
  }

  @Override
  public BigDecimal getWalletBalance(String currency) {
    String normalizedCurrency = normalize(currency, "currency");
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("currency", normalizedCurrency);
      Map<String, Object> response =
          postForm(privateClient, PATH_WALLET_BALANCE, form, Map.class, MediaType.APPLICATION_JSON);
      Object balance = response.get("balance");
      if (balance == null) throw new IllegalStateException("Empty balance");
      return new BigDecimal(String.valueOf(balance));
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public List<Quote> getQuotes() {
    List<CurrencyExchange> entries = currencyExchangeRepository.findByExchange_Name(EXCHANGE);
    if (entries == null || entries.isEmpty()) return Collections.emptyList();

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
      Map<?, ?> resp =
          publicClient
              .get()
              .uri(
                  b ->
                      b.path(PATH_STATS)
                          .queryParam("srcCurrency", base.toLowerCase(LOCALE))
                          .queryParam("dstCurrency", quote.toLowerCase(LOCALE))
                          .build())
              .retrieve()
              .body(Map.class);
      if (resp == null || resp.get("stats") == null) return null;
      Map<?, ?> stats = (Map<?, ?>) resp.get("stats");
      String key = (base + "-" + quote).toLowerCase(LOCALE);
      Object entry = stats.get(key);
      if (!(entry instanceof Map)) {
        for (var e : stats.entrySet()) {
          if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) {
            entry = e.getValue();
            break;
          }
        }
      }
      if (!(entry instanceof Map)) return null;
      Map<?, ?> s = (Map<?, ?>) entry;
      Object price = "sell".equals(side) ? s.get("bestSell") : s.get("bestBuy");
      return price == null ? null : new BigDecimal(String.valueOf(price));
    } catch (RestClientResponseException http) {
      return null;
    }
  }

  @Override
  public OrderAck submitOrder(OrderRequest r) {
    try {
      String pair = normalize(r.getSymbol(), "symbol");
      String[] parts = pair.split("[-_]");
      if (parts.length != 2) throw new IllegalArgumentException("symbol must be like 'btc-usdt'");
      String src = parts[0], dst = parts[1];

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("type", normalize(r.getSide(), "side"));
      form.add("execution", "limit");
      form.add("srcCurrency", src);
      form.add("dstCurrency", dst);
      form.add("amount", r.getQty().toPlainString());
      form.add("price", r.getPrice().toPlainString());
      form.add("clientOrderId", defaultClientOrderId(r));

      Map<String, Object> resp =
          postForm(privateClient, PATH_ORDER_ADD, form, Map.class, MediaType.APPLICATION_JSON);

      String status = String.valueOf(resp.getOrDefault("status", "unknown"));
      Object orderIdObj = resp.get("orderId");
      String orderId = orderIdObj != null ? String.valueOf(orderIdObj) : null;
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

      Map<String, Object> resp =
          postForm(
              privateClient, PATH_ORDER_UPDATE_STATUS, form, Map.class, MediaType.APPLICATION_JSON);

      String status = (String) resp.get("status");
      String updated = (String) resp.get("updatedStatus");
      return "ok".equalsIgnoreCase(status)
          || "canceled".equalsIgnoreCase(status)
          || "Canceled".equalsIgnoreCase(updated);
    } catch (RestClientResponseException http) {
      return false;
    }
  }

  private static String defaultClientOrderId(OrderRequest req) {
    return ("CLI-" + req.getSymbol() + "-" + System.currentTimeMillis()).toUpperCase(LOCALE);
  }

  private static String toSymbol(CurrencyExchange cx) {
    String exSym = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSym)) return exSym.toLowerCase(LOCALE);
    String base = cx.getCurrency().getSymbol().toLowerCase(LOCALE);
    String quote = "usdt";
    return base + "-" + quote;
  }

  private static String normalize(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.toLowerCase(LOCALE);
  }

  private static <T> T postForm(
      RestClient client,
      String path,
      MultiValueMap<String, String> form,
      Class<T> responseType,
      MediaType accept) {
    Objects.requireNonNull(client, "client");
    Objects.requireNonNull(path, "path");
    RestClient.RequestBodySpec spec =
        client.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
    if (accept != null) {
      spec = spec.accept(accept);
    }
    return spec.body(form).retrieve().body(responseType);
  }
}
