package com.arbitrage.service.exchanges;

import com.arbitrage.config.RamzinexClients;
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
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RamzinexMarketClient implements ExchangeMarketClient {

  private static final Locale LOCALE = Locale.ROOT;

  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final ExchangeAccessService exchangeAccessService;
  private final RamzinexClients ramzinexClients;

  private RestClient publicClient;
  private RestClient privateClient;
  private RestClient authenticatedRestClient;

  private static final String EXCHANGE = "RAMZINEX";
  private static final String ACCOUNT = "Ramzinex";

  private static final String P_PAIRS = "/exchange/api/v2.0/exchange/pairs";
  private static final String P_CURRENCIES = "/exchange/api/v2.0/exchange/currencies";
  private static final String P_ORDERBOOK_ONE =
      "/exchange/api/v1.0/exchange/orderbooks/{pairId}/buys_sells";
  private static final String P_FUNDS_AVAILABLE =
      "/exchange/api/v1.0/exchange/users/me/funds/available/currency/{currencyId}";
  private static final String P_ORDER_LIMIT = "/exchange/api/v1.0/exchange/users/me/orders/limit";
  private static final String P_ORDER_CANCEL =
      "/exchange/api/v1.0/exchange/users/me/orders/{orderId}/cancel";
  private static final String P_ORDER_STATUS =
      "/exchange/api/v1.0/exchange/users/me/orders/{orderId}";

  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private volatile boolean pairsLoaded = false;
  private final Map<String, Integer> symbolToPairId = new ConcurrentHashMap<>();
  private final Object pairLock = new Object();
  private volatile boolean currenciesLoaded = false;
  private final Map<String, Integer> currencyToId = new ConcurrentHashMap<>();
  private final Object currencyLock = new Object();

  public RamzinexMarketClient(
      CurrencyExchangeRepository currencyExchangeRepository,
      ExchangeAccessService exchangeAccessService,
      RamzinexClients ramzinexClients) {
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.exchangeAccessService = exchangeAccessService;
    this.ramzinexClients = ramzinexClients;
  }

  @PostConstruct
  void init() {
    Exchange exchange = exchangeAccessService.requireExchange(EXCHANGE);
    ExchangeAccount account = exchangeAccessService.requireAccount(EXCHANGE, ACCOUNT);
    this.publicClient = ramzinexClients.publicClient(exchange);
    this.privateClient = ramzinexClients.privateClient(exchange, account);
    this.authenticatedRestClient = ramzinexClients.openRestClient(exchange);
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

    final String normalizedCurrency =
        requireLower(currencyExchange.getExchangeSymbol(), "currency");
    final Integer currencyId = resolveCurrencyId(normalizedCurrency);
    try {
      Map<?, ?> resp =
          privateClient
              .get()
              .uri(b -> b.path(P_FUNDS_AVAILABLE).build(currencyId))
              .retrieve()
              .body(Map.class);
      if (resp == null) {
        throw new IllegalStateException("Empty funds response");
      }
      Object data = resp.get("data");
      return new BigDecimal(String.valueOf(data));
    } catch (RestClientResponseException http) {
      throw http;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to fetch balance for " + normalizedCurrency, ex);
    }
  }

  @Override
  public List<Quote> getQuotes() {
    List<CurrencyExchange> entries = currencyExchangeRepository.findByExchange_Name(EXCHANGE);
    if (entries == null || entries.isEmpty()) return Collections.emptyList();

    long ts = Instant.now().toEpochMilli();
    List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      // ToDo: remove this condition
      if (cx.getCurrency().getName() != null && cx.getCurrency().getName().equals("RIAL")) continue;
      String symbol = toSymbol(cx);
      Integer pairId = resolvePairId(symbol);
      Map<?, ?> ob = fetchOrderbook(pairId);
      if (ob == null) continue;
      BigDecimal bid = firstPrice((List<?>) ob.get("buys"));
      BigDecimal ask = firstPrice((List<?>) ob.get("sells"));
      if (bid != null && ask != null) {
        out.add(new Quote(symbol, bid, ask, ts));
      }
    }
    return out;
  }

  @Override
  public OrderAck submitOrder(OrderRequest r) {
    String symbol = requireLower(r.getSymbol(), "symbol");
    String side = requireLower(r.getSide(), "side"); // buy|sell
    BigDecimal qty = requirePositive(r.getQty(), "qty");
    BigDecimal price = requirePositive(r.getPrice(), "price");
    Integer pairId = resolvePairId(symbol);

    Map<String, Object> body = new HashMap<>();
    body.put("pair_id", pairId);
    body.put("amount", qty);
    body.put("price", price);
    body.put("type", side);

    try {
      Map<?, ?> resp =
          privateClient
              .post()
              .uri(P_ORDER_LIMIT)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(Map.class);

      String exOrderId = null;
      if (resp != null) {
        Object dataObj = resp.get("data");
        if (dataObj instanceof Map) {
          Map<?, ?> d = (Map<?, ?>) dataObj;
          Object oid = d.get("order_id");
          if (oid != null) exOrderId = String.valueOf(oid);
        }
      }
      String status = resp != null ? String.valueOf(resp.get("status")) : "unknown";
      String clientOrderId = createClientOrderId(symbol);
      return new OrderAck(clientOrderId, exOrderId, status);
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    if (!StringUtils.hasText(orderId) || !DIGITS.matcher(orderId).matches()) return false;
    try {
      Map<?, ?> resp =
          privateClient
              .post()
              .uri(b -> b.path(P_ORDER_CANCEL).build(orderId))
              .retrieve()
              .body(Map.class);
      return resp != null && Integer.valueOf(0).equals(resp.get("status"));
    } catch (RestClientResponseException http) {
      return false;
    }
  }

  @Override
  public ExchangeOrderStatus getOrderStatus(String orderId) {
    if (!StringUtils.hasText(orderId) || !DIGITS.matcher(orderId).matches()) {
      throw new IllegalArgumentException("orderId must be numeric");
    }

    try {
      Map<?, ?> resp =
          privateClient
              .get()
              .uri(b -> b.path(P_ORDER_STATUS).build(orderId))
              .retrieve()
              .body(Map.class);

      if (resp == null) {
        return ExchangeOrderStatus.of(OrderStatus.SENT);
      }

      Object dataObj = resp.get("data");
      String status = null;
      Map<String, Object> orderData = null;
      Map<String, Object> dataMap = castToMap(dataObj);
      if (dataMap != null) {
        Object order = dataMap.get("order");
        Map<String, Object> orderMap = castToMap(order);
        if (orderMap != null) {
          orderData = orderMap;
          Object s = orderMap.get("status");
          if (s != null) {
            status = String.valueOf(s);
          }
        }
      }
      if (status == null) {
        Object s = resp.get("status");
        status = s != null ? String.valueOf(s) : null;
      }

      BigDecimal filledQty =
          extractDecimal(orderData, "filled_amount", "filledVolume", "executed_volume");
      if (filledQty == null) {
        filledQty = extractDecimal(orderData, "done_amount", "executed_amount", "amount_filled");
      }
      BigDecimal avgPrice = extractDecimal(orderData, "avg_price", "average_price");
      BigDecimal executedNotional =
          extractDecimal(orderData, "filled_total", "done_value", "executed_value");
      if (executedNotional == null && filledQty != null) {
        BigDecimal price = avgPrice != null ? avgPrice : extractDecimal(orderData, "price");
        if (price != null) {
          executedNotional = price.multiply(filledQty, MathContext.DECIMAL64);
        }
      }

      return new ExchangeOrderStatus(mapOrderStatus(status), filledQty, avgPrice, executedNotional);
    } catch (RestClientResponseException http) {
      throw http;
    }
  }

  private OrderStatus mapOrderStatus(String status) {
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
        return OrderStatus.PARTIAL;
      case "canceled":
      case "cancelled":
        return OrderStatus.CANCELLED;
      case "new":
      case "pending":
        return OrderStatus.NEW;
      default:
        return OrderStatus.SENT;
    }
  }

  private Integer resolvePairId(String symbol) {
    Integer id = symbolToPairId.get(symbol);
    if (id != null) return id;
    if (!pairsLoaded) {
      synchronized (pairLock) {
        if (!pairsLoaded) {
          loadPairsOnce();
          pairsLoaded = true;
        }
      }
    }
    id = symbolToPairId.get(symbol);
    if (id == null) {
      throw new IllegalArgumentException("pair_id not found for " + symbol);
    }
    return id;
  }

  private Integer resolveCurrencyId(String currency) {
    Integer id = currencyToId.get(currency);
    if (id != null) return id;
    if (!currenciesLoaded) {
      synchronized (currencyLock) {
        if (!currenciesLoaded) {
          loadCurrenciesOnce();
          currenciesLoaded = true;
        }
      }
    }
    id = currencyToId.get(currency);
    if (id == null) {
      throw new IllegalArgumentException("currency_id not found for " + currency);
    }
    return id;
  }

  @SuppressWarnings("unchecked")
  private void loadPairsOnce() {
    Map<?, ?> resp = authenticatedRestClient.get().uri(P_PAIRS).retrieve().body(Map.class);
    if (resp == null) {
      throw new IllegalStateException("pairs response is empty or malformed");
    }
    Object dataObj = resp.get("data");
    if (!(dataObj instanceof Map)) {
      throw new IllegalStateException("pairs response is empty or malformed");
    }
    Map<?, ?> data = (Map<?, ?>) dataObj;
    Object pairsObj = data.get("pairs");
    if (!(pairsObj instanceof List)) {
      throw new IllegalStateException("pairs list is empty");
    }
    List<?> list = (List<?>) pairsObj;
    if (list.isEmpty()) {
      throw new IllegalStateException("pairs list is empty");
    }

    for (Object o : list) {
      if (!(o instanceof Map)) continue;
      Map<?, ?> p = (Map<?, ?>) o;

      Object idObj = p.get("id");
      if (!(idObj instanceof Number)) continue;
      Integer pairId = ((Number) idObj).intValue();

      String base = null, quote = null;

      Object bcObj = p.get("base_currency");
      if (bcObj instanceof Map) {
        Map<?, ?> bc = (Map<?, ?>) bcObj;
        Object bSymObj = bc.get("symbol");
        if (bSymObj instanceof Map) {
          Map<?, ?> bSym = (Map<?, ?>) bSymObj;
          base = asLower(bSym.get("en"));
        }
        if (base == null) base = asLower(bc.get("standard_name"));
      }

      Object qcObj = p.get("quote_currency");
      if (qcObj instanceof Map) {
        Map<?, ?> qc = (Map<?, ?>) qcObj;
        Object qSymObj = qc.get("symbol");
        if (qSymObj instanceof Map) {
          Map<?, ?> qSym = (Map<?, ?>) qSymObj;
          quote = asLower(qSym.get("en"));
        }
        if (quote == null) quote = asLower(qc.get("standard_name"));
      }

      String nameEn = null;
      Object nameObj = p.get("name");
      if (nameObj instanceof Map) {
        Map<?, ?> nm = (Map<?, ?>) nameObj;
        nameEn = asLower(nm.get("en"));
      }

      String ramzinexCode = null;
      Object tcsObj = p.get("trading_chart_settings");
      if (tcsObj instanceof Map) {
        Map<?, ?> tcs = (Map<?, ?>) tcsObj;
        ramzinexCode = asLower(tcs.get("ramzinex"));
      }

      List<String> keys = new ArrayList<>(4);
      if (base != null && quote != null) {
        keys.add(base + "-" + quote);
        keys.add(base + quote);
      }
      if (nameEn != null) {
        String k = nameEn.replace("/", "-").replace(" ", "");
        keys.add(k);
        if (quote != null) {
          int idx = k.indexOf('-');
          if (idx > 0) {
            String baseName = k.substring(0, idx);
            keys.add(baseName + "-" + quote);
          }
        }
      }
      if (ramzinexCode != null) {
        keys.add(ramzinexCode);
        if (quote != null && ramzinexCode.endsWith(quote)) {
          String maybeBase = ramzinexCode.substring(0, ramzinexCode.length() - quote.length());
          if (!maybeBase.isBlank()) keys.add(maybeBase + "-" + quote);
        }
      }

      for (String k : keys) {
        if (!StringUtils.hasText(k)) continue;
        symbolToPairId.putIfAbsent(normalize(k), pairId);
      }
    }

    if (symbolToPairId.isEmpty()) {
      throw new IllegalStateException("no pair symbols extracted");
    }
  }

  @SuppressWarnings("unchecked")
  private void loadCurrenciesOnce() {
    Map<?, ?> resp = authenticatedRestClient.get().uri(P_CURRENCIES).retrieve().body(Map.class);
    if (resp == null) {
      throw new IllegalStateException("currencies empty");
    }
    Object dataObj = resp.get("data");
    if (!(dataObj instanceof Map)) {
      throw new IllegalStateException("currencies empty");
    }
    Map<?, ?> data = (Map<?, ?>) dataObj;
    Object listObj = data.get("currencies");
    if (!(listObj instanceof List)) {
      throw new IllegalStateException("currencies list empty");
    }
    List<?> list = (List<?>) listObj;
    if (list.isEmpty()) {
      throw new IllegalStateException("currencies list empty");
    }

    Map<String, Integer> tmp = new HashMap<>();
    for (Object o : list) {
      if (!(o instanceof Map)) continue;
      Map<?, ?> cur = (Map<?, ?>) o;

      Object idObj = cur.get("id");
      if (!(idObj instanceof Number)) continue;
      Integer id = ((Number) idObj).intValue();

      Object sym = cur.get("symbol");
      if (sym instanceof String) tmp.putIfAbsent(lower((String) sym), id);

      Object nameEn = cur.get("name_en");
      if (nameEn instanceof String) tmp.putIfAbsent(lower((String) nameEn), id);
    }

    currencyToId.putAll(tmp);
  }

  private Map<?, ?> fetchOrderbook(Integer pairId) {
    try {
      Map<?, ?> resp =
          publicClient
              .get()
              .uri(b -> b.path(P_ORDERBOOK_ONE).build(pairId))
              .retrieve()
              .body(Map.class);
      if (resp == null) return null;
      Object data = resp.get("data");
      return (data instanceof Map) ? (Map<?, ?>) data : null;
    } catch (RestClientResponseException http) {
      return null;
    }
  }

  private static BigDecimal firstPrice(List<?> side) {
    if (side == null || side.isEmpty()) return null;
    Object row0 = ((List<?>) side.get(0)).get(0);
    return (row0 == null) ? null : new BigDecimal(String.valueOf(row0));
  }

  private static String toSymbol(CurrencyExchange cx) {
    // String exSym = cx.getExchangeSymbol();
    // if (StringUtils.hasText(exSym)) return exSym.toLowerCase(LOCALE);
    String base = cx.getCurrency().getSymbol().toLowerCase(LOCALE);
    String quote = "irr";
    return base + "-" + quote;
  }

  private static String normalize(String s) {
    return s.toLowerCase(LOCALE).replace(" ", "").replace("rial", "irr");
  }

  private static String lower(String v) {
    return v == null ? null : v.toLowerCase(LOCALE);
  }

  private static String asLower(Object o) {
    return o == null ? null : String.valueOf(o).trim().toLowerCase(LOCALE);
  }

  private static BigDecimal requirePositive(BigDecimal v, String f) {
    if (v == null || v.signum() <= 0) throw new IllegalArgumentException(f + " must be positive");
    return v;
  }

  private static String requireLower(String v, String f) {
    if (!StringUtils.hasText(v)) throw new IllegalArgumentException(f + " must not be blank");
    return v.toLowerCase(LOCALE);
  }

  private static String createClientOrderId(String symbol) {
    return ("CLI-" + symbol + "-" + System.currentTimeMillis()).toUpperCase(LOCALE);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castToMap(Object value) {
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
          // ignore malformed numeric values
        }
      }
    }
    return null;
  }
}
