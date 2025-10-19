package com.arbitrage.service.exchanges;

import com.arbitrage.config.NobitexClients;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.exception.OrderNotFoundException;
import com.arbitrage.model.ExchangeOrderStatus;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.ExchangeAccessService;
import com.arbitrage.service.ExchangeMarketClient;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;
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

  private static final String EXCHANGE_NAME = "NOBITEX";
  private static final String ACCOUNT_NAME = "Nobitex";

  private static final String PATH_WALLET_BALANCE = "/users/wallets/balance";
  private static final String PATH_STATS = "/market/stats";
  private static final String PATH_ORDER_ADD = "/market/orders/add";
  private static final String PATH_ORDER_UPDATE_STATUS = "/market/orders/update-status";
  private static final String PATH_ORDER_STATUS = "/market/orders/status";

  private final CurrencyExchangeRepository currencyExchangeRepo;
  private final ExchangeAccessService accessService;
  private final NobitexClients clientsFactory;

  private RestClient publicClient;
  private RestClient privateClient;

  public NobitexMarketClient(
      CurrencyExchangeRepository currencyExchangeRepository,
      ExchangeAccessService exchangeAccessService,
      NobitexClients clients) {
    this.currencyExchangeRepo = currencyExchangeRepository;
    this.accessService = exchangeAccessService;
    this.clientsFactory = clients;
  }

  @PostConstruct
  void init() {
    Exchange ex = accessService.requireExchange(EXCHANGE_NAME);
    ExchangeAccount acc = accessService.requireAccount(EXCHANGE_NAME, ACCOUNT_NAME);
    this.publicClient = clientsFactory.publicClient(ex);
    this.privateClient = clientsFactory.privateClient(ex, acc);
  }

  @Override
  public String getExchangeName() {
    return EXCHANGE_NAME;
  }

  @Override
  public BigDecimal getWalletBalance(String currency) {
    CurrencyExchange cx =
        currencyExchangeRepo.findByExchange_NameAndCurrency_Name(EXCHANGE_NAME, currency);
    if (cx == null) {
      throw new IllegalStateException("Wallet balance mapping not found for currency: " + currency);
    }

    String currencyParam =
        requireNonBlankLower(
            StringUtils.hasText(cx.getExchangeSymbol()) ? cx.getExchangeSymbol() : currency,
            "currency");

    try {
      var form = new LinkedMultiValueMap<String, String>();
      form.add("currency", currencyParam);

      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          postForm(privateClient, PATH_WALLET_BALANCE, form, Map.class, MediaType.APPLICATION_JSON);

      Object balance = (response != null) ? response.get("balance") : null;
      if (balance == null) {
        throw new IllegalStateException("Empty balance");
      }
      return new BigDecimal(String.valueOf(balance));
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public List<Quote> getQuotes() {
    List<CurrencyExchange> entries = currencyExchangeRepo.findByExchange_Name(EXCHANGE_NAME);
    if (entries == null || entries.isEmpty()) return Collections.emptyList();

    long ts = Instant.now().toEpochMilli();
    List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      String symbol = toExchangeSymbol(cx); // btc-usdt
      String[] pp = symbol.split("-");
      if (pp.length != 2) continue;

      String base = pp[0], quote = pp[1];

      BigDecimal ask = fetchBestPrice(base, quote, "sell"); // bestSell
      BigDecimal bid = fetchBestPrice(base, quote, "buy"); // bestBuy
      if (ask != null && bid != null) {
        out.add(new Quote(symbol, bid, ask, ts));
      }
    }
    return out;
  }

  private BigDecimal fetchBestPrice(String base, String quote, String side) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> resp =
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

      if (resp == null) return null;

      Object statsObj = resp.get("stats");
      if (!(statsObj instanceof Map)) return null;

      @SuppressWarnings("unchecked")
      Map<String, Object> stats = (Map<String, Object>) statsObj;

      String key = (base + "-" + quote).toLowerCase(LOCALE);
      Object entry = stats.get(key);
      if (!(entry instanceof Map)) {
        for (Map.Entry<String, Object> e : stats.entrySet()) {
          if (key.equalsIgnoreCase(e.getKey())) {
            entry = e.getValue();
            break;
          }
        }
      }
      if (!(entry instanceof Map)) return null;

      @SuppressWarnings("unchecked")
      Map<String, Object> marketStat = (Map<String, Object>) entry;
      Object price =
          "sell".equalsIgnoreCase(side) ? marketStat.get("bestSell") : marketStat.get("bestBuy");

      return (price == null) ? null : new BigDecimal(String.valueOf(price));
    } catch (RestClientResponseException http) {
      return null;
    }
  }

  @Override
  public OrderAck submitOrder(OrderRequest r) {
    String symbol = requireNonBlankLower(r.getSymbol(), "symbol");
    String[] parts = symbol.split("[-_]");
    if (parts.length != 2) {
      throw new IllegalArgumentException("symbol must be like 'btc-usdt'");
    }
    String srcCurrency = parts[0], dstCurrency = parts[1];

    String side = requireNonBlankLower(r.getSide(), "side"); // buy | sell
    BigDecimal qty = requirePositive(r.getQty(), "qty");
    BigDecimal price = requirePositive(r.getPrice(), "price");
    String clientOrderId = defaultClientOrderId(r);

    var form = new LinkedMultiValueMap<String, String>();
    form.add("type", side);
    form.add("execution", "limit");
    form.add("srcCurrency", srcCurrency);
    form.add("dstCurrency", dstCurrency);
    form.add("amount", qty.toPlainString());
    form.add("price", price.toPlainString());
    form.add("clientOrderId", clientOrderId);

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> resp =
          postForm(privateClient, PATH_ORDER_ADD, form, Map.class, MediaType.APPLICATION_JSON);

      String status = String.valueOf(resp.getOrDefault("status", "unknown"));
      String exOrderId = valueAsString(resp.get("orderId"));
      return new OrderAck(clientOrderId, exOrderId, status);
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("status", "canceled");
    if (StringUtils.hasText(orderId) && orderId.matches("\\d+")) {
      form.add("order", orderId);
    } else {
      form.add("clientOrderId", orderId);
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> resp =
          postForm(
              privateClient, PATH_ORDER_UPDATE_STATUS, form, Map.class, MediaType.APPLICATION_JSON);

      String status = valueAsString(resp.get("status"));
      String updated = valueAsString(resp.get("updatedStatus"));
      return equalsIgnoreCase(status, "ok")
          || equalsIgnoreCase(status, "canceled")
          || equalsIgnoreCase(updated, "canceled")
          || equalsIgnoreCase(updated, "Canceled");
    } catch (RestClientResponseException http) {
      return false;
    }
  }

  @Override
  public ExchangeOrderStatus getOrderStatus(String orderId) {
    if (!StringUtils.hasText(orderId)) {
      throw new IllegalArgumentException("orderId must not be blank");
    }

    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      if (orderId.matches("\\d+")) form.add("id", orderId);
      else form.add("clientOrderId", orderId);

      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          postForm(privateClient, PATH_ORDER_STATUS, form, Map.class, MediaType.APPLICATION_JSON);

      String status = valueAsString(response != null ? response.get("status") : null);
      String orderStatus = valueAsString(response != null ? response.get("orderStatus") : null);

      @SuppressWarnings("unchecked")
      Map<String, Object> orderData =
          (response != null && response.get("order") instanceof Map)
              ? (Map<String, Object>) response.get("order")
              : null;

      BigDecimal filledQty =
          parseDecimal(
              orderData,
              "matchedVolume",
              "filledVolume",
              "executedVolume",
              "matchedAmount",
              "filledAmount",
              "executedAmount");

      BigDecimal avgPrice = parseDecimal(orderData, "averagePrice", "avgPrice");
      BigDecimal executedNotional =
          parseDecimal(orderData, "matchedAmount", "filledAmount", "executedAmount");

      if (executedNotional == null && filledQty != null) {
        BigDecimal px = (avgPrice != null) ? avgPrice : parseDecimal(orderData, "price");
        if (px != null) {
          executedNotional = px.multiply(filledQty, MathContext.DECIMAL64);
        }
      }

      OrderStatus mapped = mapOrderStatus(StringUtils.hasText(orderStatus) ? orderStatus : status);

      return new ExchangeOrderStatus(mapped, filledQty, avgPrice, executedNotional);

    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == 404) {
        String body = safeBody(e);
        if (containsNotFoundMarker(body)) {
          throw new OrderNotFoundException(orderId, "Order not found: " + orderId, e);
        }
      }
      throw e;
    }
  }

  // ======== Helpers ========

  private static String valueAsString(Object v) {
    return (v == null) ? null : String.valueOf(v);
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    return a != null && a.equalsIgnoreCase(b);
  }

  private static String defaultClientOrderId(OrderRequest req) {
    return ("CLI-" + req.getSymbol() + "-" + System.currentTimeMillis()).toUpperCase(LOCALE);
  }

  private static String toExchangeSymbol(CurrencyExchange cx) {
    String exSym = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSym)) {
      return exSym.toLowerCase(LOCALE);
    }
    String base = cx.getCurrency().getSymbol().toLowerCase(LOCALE);
    String quote = "usdt";
    return base + "-" + quote;
  }

  private static String requireNonBlankLower(String v, String field) {
    if (!StringUtils.hasText(v)) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return v.toLowerCase(LOCALE);
  }

  private static BigDecimal requirePositive(BigDecimal v, String field) {
    if (v == null || v.signum() <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return v;
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

  private static BigDecimal parseDecimal(Map<String, Object> map, String... keys) {
    if (map == null || keys == null) return null;
    for (String k : keys) {
      Object v = map.get(k);
      if (v != null) {
        try {
          return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException ignore) {
          // skip and continue
        }
      }
    }
    return null;
  }

  private static String safeBody(RestClientResponseException e) {
    try {
      return e.getResponseBodyAsString();
    } catch (Exception ignore) {
      return null;
    }
  }

  private static boolean containsNotFoundMarker(String body) {
    if (body == null) return false;
    String s = body.toLowerCase(LOCALE);
    return s.contains("\"error\":\"notfound\"")
        || s.contains("no order matches the given query")
        || s.contains("not found");
  }

  private OrderStatus mapOrderStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return OrderStatus.SENT;
    }
    String s = status.trim().toLowerCase(LOCALE);
    switch (s) {
      case "done":
      case "filled":
      case "matched":
      case "closed":
        return OrderStatus.FILLED;
      case "partial":
      case "partially_filled":
      case "partial_fill":
        return OrderStatus.PARTIAL;
      case "canceled":
      case "cancelled":
        return OrderStatus.CANCELLED;
      case "new":
        return OrderStatus.NEW;
      default:
        return OrderStatus.SENT;
    }
  }
}
