package com.arbitrage.service.exchanges;

import com.arbitrage.config.WallexClients;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.model.ExchangeOrderStatus;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.ExchangeAccessService;
import com.arbitrage.service.ExchangeMarketClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class WallexMarketClient implements ExchangeMarketClient {

  private static final Locale LOCALE = Locale.ROOT;
  private static final String EXCHANGE = "WALLEX";
  private static final String ACCOUNT = "Wallex";
  private static final String PATH_MARKETS = "/hector/web/v1/markets";
  private static final String P_FUNDS_AVAILABLE = "/v1/account/balances";
  private static final String PATH_ORDER_CREATE = "/v1/account/orders";
  private static final String P_ORDER_CANCEL = "/v1/account/orders/{client_id}";
  private static final String PATH_ORDER_STATUS = "/v1/account/orders/{client_id}";
  private static final Pattern DIGITS = Pattern.compile("\\d+");

  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final ExchangeAccessService exchangeAccessService;
  private final WallexClients wallexClients;
  private RestClient publicClient;

  public WallexMarketClient(
      CurrencyExchangeRepository currencyExchangeRepository,
      ExchangeAccessService exchangeAccessService,
      WallexClients wallexClients) {
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.exchangeAccessService = exchangeAccessService;
    this.wallexClients = wallexClients;
  }

  @PostConstruct
  void init() {
    Exchange exchange = exchangeAccessService.requireExchange(EXCHANGE);
    ExchangeAccount account = exchangeAccessService.requireAccount(EXCHANGE, ACCOUNT);
    this.publicClient = wallexClients.client(exchange, account);
  }

  @Override
  public String getExchangeName() {
    return EXCHANGE;
  }

  @Override
  public BigDecimal getWalletBalance(String currency) {
    CurrencyExchange currencyExchange =
        currencyExchangeRepository.findByExchange_NameAndCurrency_Name(EXCHANGE, currency);
    if (currencyExchange == null) throw new IllegalStateException("Empty funds response");

    try {
      Map<?, ?> response =
          publicClient
              .get()
              .uri(uriBuilder -> uriBuilder.path(P_FUNDS_AVAILABLE).build())
              .retrieve()
              .body(Map.class);

      if (response == null) throw new IllegalStateException("Empty funds response");

      Map result = (Map) response.get("result");
      Map balances = (Map) result.get("balances");
      Map data = (Map) balances.get(currencyExchange.getExchangeSymbol());
      return new BigDecimal(String.valueOf(data.get("value")));
    } catch (RestClientResponseException http) {
      throw http;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to fetch balance for " + currency, ex);
    }
  }

  @Override
  public List<Quote> getQuotes() {
    List<CurrencyExchange> exchanges = currencyExchangeRepository.findByExchange_Name(EXCHANGE);
    if (exchanges == null || exchanges.isEmpty()) return Collections.emptyList();

    long timestamp = Instant.now().toEpochMilli();
    Map<String, WallexMarket> marketMap = fetchMarketsIndex();

    return exchanges.stream()
        .map(cx -> toQuote(cx, marketMap, timestamp))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Quote toQuote(CurrencyExchange cx, Map<String, WallexMarket> marketMap, long timestamp) {
    String symbol = resolveSymbol(cx);
    String[] parts = symbol.split("-");
    if (parts.length != 2) return null;

    BigDecimal ask = bestPrice(marketMap, parts[0], parts[1], "sell");
    BigDecimal bid = bestPrice(marketMap, parts[0], parts[1], "buy");

    return (ask != null && bid != null) ? new Quote(symbol, bid, ask, timestamp) : null;
  }

  private String resolveSymbol(CurrencyExchange cx) {
    if (StringUtils.hasText(cx.getExchangeSymbol()))
      return cx.getExchangeSymbol().toLowerCase(LOCALE);
    return cx.getCurrency().getSymbol().toLowerCase(LOCALE) + "-usdt";
  }

  private Map<String, WallexMarket> fetchMarketsIndex() {
    try {
      WallexMarketsResponse response =
          publicClient
              .get()
              .uri(PATH_MARKETS)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(WallexMarketsResponse.class);

      if (response == null
          || response.getResult() == null
          || response.getResult().getMarkets() == null) return Collections.emptyMap();

      return response.getResult().getMarkets().stream()
          .collect(
              Collectors.toMap(
                  m ->
                      m.getBaseAsset().toLowerCase(LOCALE)
                          + "-"
                          + m.getQuoteAsset().toLowerCase(LOCALE),
                  m -> m,
                  (a, b) -> a));
    } catch (Exception e) {
      log.warn("Error fetching Wallex market index: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }

  private BigDecimal bestPrice(
      Map<String, WallexMarket> marketMap, String base, String quote, String side) {
    WallexMarket market = marketMap.get(base.toLowerCase(LOCALE) + "-" + quote.toLowerCase(LOCALE));
    if (market == null || market.getFairPrice() == null) return null;

    String priceStr =
        "sell".equals(side) ? market.getFairPrice().getAsk() : market.getFairPrice().getBid();
    return (priceStr != null && !priceStr.isBlank()) ? new BigDecimal(priceStr) : null;
  }

  @Override
  public OrderAck submitOrder(OrderRequest request) {
    String symbol = normalizeSymbol(requireText(request.getSymbol(), "symbol"));
    String side = requireLower(request.getSide(), "side");
    BigDecimal qty = requirePositive(request.getQty(), "qty");
    BigDecimal price = requirePositive(request.getPrice(), "price");
    String clientOrderId = createClientOrderId(symbol);

    Map<String, Object> body =
        new HashMap<>() {
          {
            put("symbol", symbol);
            put("side", side);
            put("type", "LIMIT");
            put("price", price.toPlainString());
            put("quantity", qty.toPlainString());
            put("client_id", clientOrderId);
          }
        };

    try {
      Map<?, ?> response =
          publicClient
              .post()
              .uri(PATH_ORDER_CREATE)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(Map.class);

      if (response != null && response.get("result") instanceof Map) {
        Map result = (Map) response.get("result");
        String exOrderId = String.valueOf(result.get("clientOrderId"));
        String status =
            Optional.ofNullable(result.get("status")).map(String::valueOf).orElse("unknown");
        return new OrderAck(clientOrderId, exOrderId, status);
      }
      return new OrderAck(clientOrderId, null, "unknown");

    } catch (RestClientException e) {
      log.error("Wallex order submit error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    if (!StringUtils.hasText(orderId) || !DIGITS.matcher(orderId).matches()) return false;
    try {
      Map<?, ?> response =
          publicClient
              .delete()
              .uri(uriBuilder -> uriBuilder.path(P_ORDER_CANCEL).build(orderId))
              .retrieve()
              .body(Map.class);
      return response != null && Integer.valueOf(0).equals(response.get("status"));
    } catch (RestClientException e) {
      log.warn("Cancel order failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public ExchangeOrderStatus getOrderStatus(String orderId) {
    if (!StringUtils.hasText(orderId) || !DIGITS.matcher(orderId).matches()) {
      throw new IllegalArgumentException("orderId must be numeric");
    }

    try {
      Map<?, ?> response =
          publicClient
              .get()
              .uri(uriBuilder -> uriBuilder.path(PATH_ORDER_STATUS).build(orderId))
              .retrieve()
              .body(Map.class);

      WallexOrderSnapshot snapshot = WallexOrderSnapshot.fromResponse(response);
      return snapshot.toExchangeOrderStatus();
    } catch (RestClientException e) {
      log.warn("Failed to fetch Wallex order status: {}", e.getMessage());
      throw e;
    }
  }

  private static OrderStatus mapOrderStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return OrderStatus.SENT;
    }

    String normalized = status.trim().toLowerCase(LOCALE);
    switch (normalized) {
      case "filled":
      case "done":
      case "closed":
        return OrderStatus.FILLED;
      case "partial":
      case "partial_fill":
      case "partially_filled":
      case "partial-filled":
      case "partially-filled":
      case "partialfilled":
      case "partiallyfilled":
        return OrderStatus.PARTIAL;
      case "canceled":
      case "cancelled":
      case "rejected":
      case "expired":
      case "failed":
      case "cancelled_by_user":
      case "cancelled_by_system":
        return OrderStatus.CANCELLED;
      case "new":
      case "pending":
      case "open":
      case "opened":
      case "oppend":
      case "waiting":
      case "pending_cancel":
      case "processing":
      case "in_progress":
      case "in-progress":
        return OrderStatus.NEW;
      default:
        return OrderStatus.SENT;
    }
  }

  private static final class WallexOrderSnapshot {
    private static final WallexOrderSnapshot EMPTY =
        new WallexOrderSnapshot(OrderStatus.SENT, null, null, null);

    private final OrderStatus status;
    private final BigDecimal filledQty;
    private final BigDecimal avgPrice;
    private final BigDecimal executedNotional;

    private WallexOrderSnapshot(
        OrderStatus status,
        BigDecimal filledQty,
        BigDecimal avgPrice,
        BigDecimal executedNotional) {
      this.status = status;
      this.filledQty = filledQty;
      this.avgPrice = avgPrice;
      this.executedNotional = executedNotional;
    }

    static WallexOrderSnapshot fromResponse(Map<?, ?> rawResponse) {
      if (rawResponse == null) {
        return EMPTY;
      }

      Map<String, Object> response = mapOrEmpty(rawResponse);
      Object resultCandidate = response.get("result");
      Map<String, Object> result = mapOrEmpty(resultCandidate);
      if (result.isEmpty() && resultCandidate instanceof List<?>) {
        result = firstMap((List<?>) resultCandidate);
      }
      Map<String, Object> payload = resolvePayload(result);

      List<Map<String, Object>> contexts = Arrays.asList(payload, result, response);

      String statusValue =
          firstNonBlank(
              firstText(
                  contexts,
                  "status",
                  "state",
                  "orderStatus",
                  "order_status",
                  "current_status",
                  "orderState"),
              asString(response.get("status")),
              asString(response.get("resultStatus")));

      OrderStatus mappedStatus = mapOrderStatus(statusValue);

      BigDecimal filledQty =
          firstDecimal(
              contexts,
              "executedQty",
              "executed_quantity",
              "executedQuantity",
              "filled_quantity",
              "filledQuantity",
              "filledAmount",
              "quantityFilled",
              "executed_amount",
              "executedAmount");

      BigDecimal avgPrice =
          firstDecimal(
              contexts,
              "executedPrice",
              "executed_price",
              "average_price",
              "avg_price",
              "avgPrice",
              "averagePrice");

      BigDecimal executedNotional =
          firstDecimal(
              contexts,
              "executedSum",
              "executed_notional",
              "executed_notional_value",
              "filled_notional",
              "value",
              "executedValue",
              "executed_quote",
              "executedQuote",
              "filled_value",
              "cummulativeQuoteQty");

      if (executedNotional == null && filledQty != null) {
        BigDecimal priceCandidate =
            avgPrice != null
                ? avgPrice
                : firstDecimal(contexts, "price", "executedPrice", "orderPrice");
        if (priceCandidate != null) {
          executedNotional = priceCandidate.multiply(filledQty, MathContext.DECIMAL64);
        }
      }

      if (avgPrice == null
          && executedNotional != null
          && filledQty != null
          && filledQty.signum() > 0) {
        avgPrice = executedNotional.divide(filledQty, MathContext.DECIMAL64);
      }

      return new WallexOrderSnapshot(mappedStatus, filledQty, avgPrice, executedNotional);
    }

    ExchangeOrderStatus toExchangeOrderStatus() {
      return new ExchangeOrderStatus(status, filledQty, avgPrice, executedNotional);
    }

    private static Map<String, Object> mapOrEmpty(Object candidate) {
      Map<String, Object> map = asMap(candidate);
      return map != null ? map : Collections.emptyMap();
    }

    private static Map<String, Object> resolvePayload(Map<String, Object> result) {
      List<String> orderKeys =
          Arrays.asList("order", "order_info", "data", "detail", "info", "orderDetail", "payload");
      for (String key : orderKeys) {
        Object candidate = result.get(key);
        Map<String, Object> map = mapOrEmpty(candidate);
        if (map.isEmpty() && candidate instanceof List<?>) {
          map = firstMap((List<?>) candidate);
        }
        if (!map.isEmpty()) {
          return map;
        }
      }
      return result;
    }

    private static Map<String, Object> firstMap(List<?> candidates) {
      if (candidates == null) {
        return Collections.emptyMap();
      }
      for (Object candidate : candidates) {
        Map<String, Object> map = mapOrEmpty(candidate);
        if (!map.isEmpty()) {
          return map;
        }
      }
      return Collections.emptyMap();
    }

    private static String firstNonBlank(String... values) {
      if (values == null) {
        return null;
      }
      for (String value : values) {
        if (StringUtils.hasText(value)) {
          return value;
        }
      }
      return null;
    }

    private static String firstText(List<Map<String, Object>> contexts, String... keys) {
      if (contexts == null || keys == null) {
        return null;
      }
      for (Map<String, Object> context : contexts) {
        if (context == null || context.isEmpty()) {
          continue;
        }
        for (String key : keys) {
          String value = asString(context.get(key));
          if (StringUtils.hasText(value)) {
            return value;
          }
        }
      }
      return null;
    }

    private static BigDecimal firstDecimal(List<Map<String, Object>> contexts, String... keys) {
      if (contexts == null || keys == null) {
        return null;
      }
      for (Map<String, Object> context : contexts) {
        BigDecimal value = extractDecimal(context, keys);
        if (value != null) {
          return value;
        }
      }
      return null;
    }
  }

  private static String normalizeSymbol(String s) {
    return s.trim().replace("-", "").replace("_", "").toUpperCase(LOCALE);
  }

  private static String requireText(String v, String name) {
    Assert.hasText(v, name + " must not be blank");
    return v;
  }

  private static String requireLower(String v, String name) {
    Assert.hasText(v, name + " must not be blank");
    return v.toLowerCase(LOCALE);
  }

  private static BigDecimal requirePositive(BigDecimal v, String name) {
    Assert.notNull(v, name + " must not be null");
    if (v.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    return v;
  }

  private static String createClientOrderId(String symbol) {
    return symbol
        + "-"
        + Instant.now().toEpochMilli()
        + "-"
        + UUID.randomUUID().toString().substring(0, 8);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    return null;
  }

  private static BigDecimal extractDecimal(Map<String, Object> map, String... keys) {
    if (map == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Object value = map.get(key);
      if (value != null) {
        try {
          return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignore) {
          // skip malformed numeric values
        }
      }
    }
    return null;
  }

  private static String asString(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WallexMarket {
    private String symbol;

    @JsonProperty("base_asset")
    private String baseAsset;

    @JsonProperty("quote_asset")
    private String quoteAsset;

    @JsonProperty("fair_price")
    private FairPrice fairPrice;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FairPrice {
      private String ask;
      private String bid;
      private String threshold;
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WallexMarketsResult {
    private List<WallexMarket> markets;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WallexMarketsResponse {
    private WallexMarketsResult result;
    private boolean success;
    private String message;
  }
}
