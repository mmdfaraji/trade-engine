package com.arbitrage.service.market;

import com.arbitrage.config.RamzinexClientsBuilder;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.ExchangeAccessService;
import com.arbitrage.service.api.ExchangeMarketClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RamzinexMarketClient implements ExchangeMarketClient {

  private final CurrencyExchangeRepository currencyExchangeRepo;

  private final ExchangeAccessService accessService;
  private final RamzinexClientsBuilder clientsBuilder;

  private RestClient ramzinexPublicRestClient;
  private RestClient ramzinexPrivateRestClient;

  @Qualifier("ramzinexOpenRestClient")
  private final RestClient ramzinexOpenRestClient;

  private final String exchangeName = "RAMZINEX";
  private final String accountLabel = "Ramzinex";

  private static final String P_PAIRS = "/exchange/api/v2.0/exchange/pairs";
  private static final String P_CURRENCIES = "/exchange/api/v2.0/exchange/currencies";
  private static final String P_ORDERBOOK_ONE =
      "/exchange/api/v1.0/exchange/orderbooks/{pairId}/buys_sells";
  private static final String P_FUNDS_AVAILABLE =
      "/exchange/api/v1.0/exchange/users/me/funds/available/currency/{currencyId}";
  private static final String P_ORDER_LIMIT = "/exchange/api/v1.0/exchange/users/me/orders/limit";
  private static final String P_ORDER_CANCEL =
      "/exchange/api/v1.0/exchange/users/me/orders/{orderId}/cancel";

  private static final Pattern DIGITS = Pattern.compile("\\d+");

  private final Map<String, Integer> symbolToPairId = new ConcurrentHashMap<>();
  private volatile boolean pairsLoaded;

  private final Map<String, Integer> currencyToId = new ConcurrentHashMap<>();
  private volatile boolean currenciesLoaded;

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SimpleStatus(
      @JsonProperty("status") int status, @JsonProperty("data") Object data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OrderbookResp(
      @JsonProperty("status") int status, @JsonProperty("data") OrderbookData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OrderbookData(
      @JsonProperty("buys") List<List<Object>> buys,
      @JsonProperty("sells") List<List<Object>> sells) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LimitOrderReq(
      @JsonProperty("pair_id") Integer pairId,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("price") BigDecimal price,
      @JsonProperty("type") String type) {} // "buy"|"sell"

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LimitOrderResp(
      @JsonProperty("status") int status, @JsonProperty("data") Map<String, Object> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CurrenciesPayload(
      @JsonProperty("status") int status, @JsonProperty("data") CurrenciesData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CurrenciesData(@JsonProperty("currencies") List<CurrencyRow> currencies) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CurrencyRow(
      @JsonProperty("id") Integer id,
      @JsonProperty("symbol") String symbol,
      @JsonProperty("name_en") String nameEn) {}

  @PostConstruct
  void init() {
    Exchange ex = accessService.requireExchange(exchangeName);
    ExchangeAccount acc = accessService.requireAccount(exchangeName, accountLabel);
    this.ramzinexPublicRestClient = clientsBuilder.buildPublic(ex);
    this.ramzinexPrivateRestClient = clientsBuilder.buildPrivate(ex, acc);
  }

  @Override
  public BigDecimal getWalletBalance(String currency) {
    final String cur = requireNonBlankLower(currency, "currency");
    final Integer currencyId = resolveCurrencyId(cur);
    try {
      SimpleStatus response =
          ramzinexPrivateRestClient
              .get()
              .uri(
                  "/exchange/api/v1.0/exchange/users/me/funds/available/currency/{currencyId}",
                  b -> b.build(currencyId))
              .retrieve()
              .body(SimpleStatus.class);

      if (response == null) throw new IllegalStateException("Empty funds response");
      return new BigDecimal(String.valueOf(response.data()));
    } catch (RestClientResponseException http) {
      log.error("RAMZINEX funds HTTP {}: {}", http.getRawStatusCode(), safeBody(http));
      throw http;
    } catch (Exception ex) {
      log.error("RAMZINEX funds error {}: {}", cur, ex.toString());
      throw new IllegalStateException("Failed to fetch RAMZINEX balance for " + cur, ex);
    }
  }

  @Override
  public List<Quote> getQuotes() {
    var entries = currencyExchangeRepo.findByExchange_Name(exchangeName);
    if (entries == null || entries.isEmpty()) return List.of();

    long ts = Instant.now().toEpochMilli();
    List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      String symbol = normalizeSymbol(toSymbol(cx)); // e.g. "btc-irr"
      Integer pairId = resolvePairId(symbol);
      var ob = fetchOrderbook(pairId);
      if (ob == null) {
        log.warn("Skip {} (pairId={}) no orderbook", symbol, pairId);
        continue;
      }
      BigDecimal bid = firstPrice(ob.buys()); // best bid
      BigDecimal ask = firstPrice(ob.sells()); // best ask
      if (bid != null && ask != null) {
        out.add(new Quote(symbol, bid, ask, ts));
      } else {
        log.warn("Skip {}: bid={} ask={}", symbol, bid, ask);
      }
    }
    return out;
  }

  @Override
  public OrderAck submitOrder(OrderRequest orderRequest) {
    final String symbol = normalizeSymbol(requireNonBlankLower(orderRequest.getSymbol(), "symbol"));
    final String side = requireNonBlankLower(orderRequest.getSide(), "side"); // buy|sell
    final BigDecimal qty = requirePositive(orderRequest.getQty(), "qty");
    final BigDecimal price = requirePositive(orderRequest.getPrice(), "price");

    final Integer pairId = resolvePairId(symbol);

    try {
      LimitOrderResp response =
          ramzinexPrivateRestClient
              .post()
              .uri(P_ORDER_LIMIT)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new LimitOrderReq(pairId, qty, price, side))
              .retrieve()
              .body(LimitOrderResp.class);

      String exOrderId =
          response != null && response.data() != null
              ? asString(response.data().get("order_id"))
              : null;
      String status = response != null ? String.valueOf(response.status()) : "unknown";
      String clientOrderId = defaultClientOrderId(orderRequest);

      return new OrderAck(clientOrderId, exOrderId, status);
    } catch (RestClientResponseException http) {
      log.error(
          "RAMZINEX placeOrder HTTP {} sym={} body={}",
          http.getRawStatusCode(),
          symbol,
          safeBody(http));
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    if (!StringUtils.hasText(orderId) || !DIGITS.matcher(orderId).matches()) {
      log.warn("cancelOrder requires numeric order_id on Ramzinex, got: {}", orderId);
      return false;
    }
    try {
      SimpleStatus response =
          ramzinexPrivateRestClient
              .post()
              .uri(P_ORDER_CANCEL, b -> b.build(orderId))
              .retrieve()
              .body(SimpleStatus.class);

      // success example: {"description":"ok","status":0,"data":[]}
      boolean ok = response != null && response.status() == 0;
      if (!ok) log.warn("Cancel not confirmed: {}", response);
      return ok;
    } catch (RestClientResponseException http) {
      log.error(
          "RAMZINEX cancel HTTP {} id={} body={}",
          http.getRawStatusCode(),
          orderId,
          safeBody(http));
      return false;
    } catch (Exception ex) {
      log.error("RAMZINEX cancel error id={}: {}", orderId, ex.toString());
      return false;
    }
  }

  private Integer resolvePairId(String rawSymbol) {
    final String symbol = normalizeSymbol(rawSymbol);
    Integer id = symbolToPairId.get(symbol);
    if (id != null) return id;

    if (!pairsLoaded) {
      synchronized (symbolToPairId) {
        if (!pairsLoaded) {
          loadPairsOnce();
          pairsLoaded = true;
        }
      }
    }
    id = symbolToPairId.get(symbol);
    if (id == null) throw new IllegalArgumentException("pair_id not found for symbol " + symbol);
    return id;
  }

  private Integer resolveCurrencyId(String currency) {
    final String key = normalizeCurrency(currency);

    Integer id = currencyToId.get(key);
    if (id != null) return id;

    if (!currenciesLoaded) {
      synchronized (currencyToId) {
        if (!currenciesLoaded) {
          loadCurrenciesOnce();
          currenciesLoaded = true;
        }
      }
    }

    id = currencyToId.get(key);
    if (id == null) throw new IllegalArgumentException("currency_id not found for " + key);
    return id;
  }

  @SuppressWarnings("unchecked")
  private void loadPairsOnce() {
    try {
      Map<String, Object> response =
          ramzinexOpenRestClient.get().uri(P_PAIRS).retrieve().body(Map.class);

      if (response == null) {
        throw new IllegalStateException("pairs response is null");
      }
      Object dataObj = response.get("data");
      if (!(dataObj instanceof Map)) {
        throw new IllegalStateException("pairs response is empty or malformed");
      }
      Map<String, Object> data = (Map<String, Object>) dataObj;

      Object pairsObj = data.get("pairs");
      if (!(pairsObj instanceof List)) {
        throw new IllegalStateException("pairs list is empty");
      }
      List<?> pairs = (List<?>) pairsObj;
      if (pairs.isEmpty()) {
        throw new IllegalStateException("pairs list is empty");
      }

      for (Object o : pairs) {
        if (!(o instanceof Map)) continue;
        Map<String, Object> p = (Map<String, Object>) o;

        // id
        Object idObj = p.get("id");
        if (!(idObj instanceof Number)) continue;
        Integer pairId = ((Number) idObj).intValue();

        // base / quote via base_currency.symbol.en & quote_currency.symbol.en
        String base = null, quote = null;

        Object bcObj = p.get("base_currency");
        if (bcObj instanceof Map) {
          Map<String, Object> bc = (Map<String, Object>) bcObj;
          Object bSymObj = bc.get("symbol");
          if (bSymObj instanceof Map) {
            Map<String, Object> bSym = (Map<String, Object>) bSymObj;
            base = asLower(bSym.get("en"));
          }
          if (base == null) {
            base = asLower(bc.get("standard_name"));
          }
        }

        Object qcObj = p.get("quote_currency");
        if (qcObj instanceof Map) {
          Map<String, Object> qc = (Map<String, Object>) qcObj;
          Object qSymObj = qc.get("symbol");
          if (qSymObj instanceof Map) {
            Map<String, Object> qSym = (Map<String, Object>) qSymObj;
            quote = asLower(qSym.get("en"));
          }
          if (quote == null) {
            quote = asLower(qc.get("standard_name"));
          }
        }

        // name.en (e.g. "ramzfix/rial")
        String nameEn = null;
        Object nameObj = p.get("name");
        if (nameObj instanceof Map) {
          Map<String, Object> nm = (Map<String, Object>) nameObj;
          nameEn = asLower(nm.get("en"));
        }

        // trading_chart_settings.ramzinex (e.g. "rfxirr")
        String ramzinexCode = null;
        Object tcsObj = p.get("trading_chart_settings");
        if (tcsObj instanceof Map) {
          Map<String, Object> tcs = (Map<String, Object>) tcsObj;
          ramzinexCode = asLower(tcs.get("ramzinex"));
        }

        // build aliases and insert
        List<String> keys = new ArrayList<>(4);

        if (base != null && quote != null) {
          keys.add(base + "-" + quote); // rfx-irr
          keys.add(base + quote); // rfxirr
        }
        if (nameEn != null) {
          String k = nameEn.replace("/", "-").replace(" ", "");
          keys.add(k);
          if (quote != null) {
            int idx = k.indexOf('-');
            if (idx > 0) {
              String baseName = k.substring(0, idx);
              keys.add(baseName + "-" + quote); // ramzfix-irr
            }
          }
        }
        if (ramzinexCode != null) {
          keys.add(ramzinexCode); // rfxirr
          if (quote != null && base == null && ramzinexCode.endsWith(quote)) {
            String maybeBase = ramzinexCode.substring(0, ramzinexCode.length() - quote.length());
            if (!maybeBase.isBlank()) {
              keys.add(maybeBase + "-" + quote);
            }
          }
        }

        for (String k : keys) {
          if (!StringUtils.hasText(k)) continue;
          symbolToPairId.putIfAbsent(normalizeSymbol(k), pairId);
        }
      }

      if (symbolToPairId.isEmpty()) {
        throw new IllegalStateException("no pair symbols extracted");
      }

    } catch (RestClientResponseException http) {
      throw new IllegalStateException(
          "Failed to load pairs: HTTP " + http.getRawStatusCode() + " body=" + safeBody(http),
          http);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load pairs: " + e.getMessage(), e);
    }
  }

  private void loadCurrenciesOnce() {
    try {
      var response =
          ramzinexOpenRestClient.get().uri(P_CURRENCIES).retrieve().body(CurrenciesPayload.class);

      if (response == null || response.data() == null || response.data().currencies() == null) {
        throw new IllegalStateException("currencies empty");
      }

      Map<String, Integer> tmp = new HashMap<>();
      for (var cur : response.data().currencies()) {
        if (cur.id() == null) continue;
        if (StringUtils.hasText(cur.symbol()))
          tmp.putIfAbsent(normalizeCurrency(cur.symbol()), cur.id());
        if (StringUtils.hasText(cur.nameEn()))
          tmp.putIfAbsent(normalizeCurrency(cur.nameEn()), cur.id());
      }
      currencyToId.putAll(tmp);
      if (currencyToId.isEmpty()) throw new IllegalStateException("currencies map empty");
    } catch (RestClientResponseException http) {
      throw new IllegalStateException(
          "Failed to load currencies: HTTP " + http.getRawStatusCode() + " body=" + safeBody(http),
          http);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load currencies: " + e.getMessage(), e);
    }
  }

  private @Nullable OrderbookData fetchOrderbook(Integer pairId) {
    try {
      OrderbookResp response =
          ramzinexPublicRestClient
              .get()
              .uri(P_ORDERBOOK_ONE, b -> b.build(pairId))
              .retrieve()
              .body(OrderbookResp.class);
      return response != null ? response.data() : null;
    } catch (RestClientResponseException http) {
      log.error(
          "RAMZINEX orderbook HTTP {} pairId={} body={}",
          http.getRawStatusCode(),
          pairId,
          safeBody(http));
      return null;
    } catch (Exception ex) {
      log.error("RAMZINEX orderbook error pairId={}: {}", pairId, ex.toString());
      return null;
    }
  }

  private static BigDecimal firstPrice(List<List<Object>> side) {
    if (side == null || side.isEmpty() || side.get(0).isEmpty()) return null;
    Object p = side.get(0).get(0);
    return (p == null) ? null : new BigDecimal(String.valueOf(p));
  }

  private static String defaultClientOrderId(OrderRequest request) {
    return (request.getSide() + "-" + request.getSymbol() + "-" + System.currentTimeMillis())
        .toUpperCase(Locale.ROOT);
  }

  private static String requireNonBlankLower(String v, String f) {
    if (!StringUtils.hasText(v)) throw new IllegalArgumentException(f + " must not be blank");
    return v.toLowerCase(Locale.ROOT);
  }

  private static BigDecimal requirePositive(BigDecimal v, String f) {
    if (v == null || v.signum() <= 0) throw new IllegalArgumentException(f + " must be positive");
    return v;
  }

  private static String safeBody(RestClientResponseException e) {
    try {
      return e.getResponseBodyAsString();
    } catch (Exception ignored) {
      return "<unavailable>";
    }
  }

  private static String asLower(Object o) {
    return (o == null) ? null : String.valueOf(o).trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeSymbol(String v) {
    if (v == null) return null;
    String s =
        v.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("/", "-").replace("rial", "irr");
    return s;
  }

  private static String normalizeCurrency(String v) {
    if (v == null) return null;
    String s = v.trim().toLowerCase(Locale.ROOT);
    return "rial".equals(s) ? "irr" : s;
  }

  private String toSymbol(CurrencyExchange cx) {
    if (cx == null) return null;
    String exSym = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSym)) return normalizeSymbol(exSym);
    // fallback: base-irr
    String base = cx.getCurrency() != null ? asLower(cx.getCurrency().getSymbol()) : null;
    return normalizeSymbol((base == null ? "" : base) + "-irr");
  }

  private static String asString(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
