package com.arbitrage.service.market;

import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.api.ExchangeMarketClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class NobitexMarketClient implements ExchangeMarketClient {

  private final CurrencyExchangeRepository currencyExchangeRepo;

  @Value("${exchanges.nobitex.name:NOBITEX}")
  private String exchangeName;

  @Qualifier("nobitexPublicRestClient")
  private final RestClient nobitexPublicRestClient;

  @Qualifier("nobitexPrivateRestClient")
  private final RestClient nobitexPrivateRestClient;

  private static final String PATH_STATS = "/market/stats";
  private static final String PATH_BALANCE = "/users/wallets/balance";
  private static final String PATH_ORDER_ADD = "/market/orders/add";
  private static final String PATH_ORDER_UPDATE = "/market/orders/update-status";
  private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

  private record BalanceResponse(String balance, String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record StatsResponse(
      @JsonProperty("status") String status,
      @JsonProperty("stats") Map<String, MarketStat> stats) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MarketStat(
      @JsonProperty("bestSell") String bestSell, // ask
      @JsonProperty("bestBuy") String bestBuy // bid
      ) {}

  private record SymbolParts(String base, String quote) {}

  @Override
  public BigDecimal getWalletBalance(String currency) {
    final String ccy = requireNonBlankLower(currency, "currency");
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("currency", ccy);

      BalanceResponse resp =
          nobitexPrivateRestClient
              .post()
              .uri(PATH_BALANCE)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(BalanceResponse.class);

      if (resp == null || resp.balance() == null || resp.status() == null) {
        throw new IllegalStateException("Empty response from Nobitex balance API");
      }
      return new BigDecimal(resp.balance());

    } catch (RestClientResponseException httpEx) {
      log.error("Nobitex balance HTTP {}: {}", httpEx.getRawStatusCode(), safeBody(httpEx));
      throw httpEx;
    } catch (Exception ex) {
      log.error("Nobitex balance error for {}: {}", ccy, ex.toString());
      throw new IllegalStateException("Failed to fetch wallet balance for " + ccy, ex);
    }
  }

  @Override
  public List<Quote> getQuotes() {
    final var entries = currencyExchangeRepo.findByExchange_Name(exchangeName);
    if (entries == null || entries.isEmpty()) {
      log.info("No symbols for exchange '{}'", exchangeName);
      return List.of();
    }

    final long ts = Instant.now().toEpochMilli();
    final List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      final var parts = symbolParts(cx);
      final String base = parts.base();
      final String quote = parts.quote();

      final BigDecimal ask = bestPrice(base, quote, OrderSide.SELL); // lowest ask
      final BigDecimal bid = bestPrice(base, quote, OrderSide.BUY); // highest bid

      if (ask != null && bid != null) {
        out.add(new Quote(base + "-" + quote, bid, ask, ts));
      } else {
        log.warn("Skip {}-{}: ask={} bid={}", base, quote, ask, bid);
      }
    }

    return out;
  }

  @Override
  public OrderAck submitOrder(OrderRequest orderRequest) {
    // Validation & normalization
    final String symbol = requireNonBlankLower(orderRequest.getSymbol(), "symbol");
    final String side = requireNonBlankLower(orderRequest.getSide(), "side"); // buy | sell
    final BigDecimal qty = requirePositive(orderRequest.getQty(), "qty");
    final BigDecimal price = requirePositive(orderRequest.getPrice(), "price");

    final String[] pair = symbol.split("[-_]");
    if (pair.length != 2) throw new IllegalArgumentException("symbol must be like 'btc-usdt'");
    final String srcCurrency = pair[0];
    final String dstCurrency = pair[1];

    final String clientOrderId = defaultClientOrderId(orderRequest);

    try {
      final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("type", side); // buy | sell
      form.add("execution", "limit"); // limit (market => remove price & set execution=market)
      form.add("srcCurrency", srcCurrency);
      form.add("dstCurrency", dstCurrency);
      form.add("amount", qty.toPlainString());
      form.add("price", price.toPlainString());
      form.add("clientOrderId", clientOrderId);

      @SuppressWarnings("unchecked")
      final Map<String, Object> resp =
          nobitexPrivateRestClient
              .post()
              .uri(PATH_ORDER_ADD)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(Map.class);

      final String exchangeOrderId = resp != null ? asString(resp.get("orderId")) : null;
      final String status = resp != null ? resp.get("status").toString() : "unknown";

      return new OrderAck(clientOrderId, exchangeOrderId, status);

    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} placeOrder symbol={} body={}", http.getRawStatusCode(), symbol, safeBody(http));
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    if (!StringUtils.hasText(orderId)) return false;
    try {
      final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("status", "canceled");

      if (DIGITS_ONLY.matcher(orderId).matches()) {
        form.add("order", orderId); // exchange order id (numeric)
      } else {
        form.add("clientOrderId", orderId); // client order id (string)
      }

      @SuppressWarnings("unchecked")
      final Map<String, Object> resp =
          nobitexPrivateRestClient
              .post()
              .uri(PATH_ORDER_UPDATE) // apiv2 absolute URL
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(Map.class);

      final String status = resp != null ? asString(resp.get("status")) : null;
      final String updated = resp != null ? asString(resp.get("updatedStatus")) : null;

      final boolean ok =
          equalsIgnoreCase(status, "ok")
              || equalsIgnoreCase(status, "canceled")
              || equalsIgnoreCase(updated, "canceled");

      if (!ok) {
        log.warn(
            "Cancel not confirmed. status={}, updatedStatus={}, code={}, message={}",
            status,
            updated,
            asString(resp != null ? resp.get("code") : null),
            asString(resp != null ? resp.get("message") : null));
      }

      return ok;
    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} cancelOrder id={} body={}", http.getRawStatusCode(), orderId, safeBody(http));
      return false;
    } catch (Exception ex) {
      log.error("Error cancelOrder id={}: {}", orderId, ex.toString());
      return false;
    }
  }

  /**
   * اگر exchangeSymbol مثل "btc-usdt" ست بود، همان را می‌شکند؛ وگرنه از فیلدهای موجود entity
   * استفاده می‌کند و به fallback می‌رود.
   */
  private SymbolParts symbolParts(CurrencyExchange cx) {
    final String exSymbol = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSymbol) && exSymbol.contains("-")) {
      final String[] sp = exSymbol.toLowerCase(Locale.ROOT).split("-");
      if (sp.length == 2 && StringUtils.hasText(sp[0]) && StringUtils.hasText(sp[1])) {
        return new SymbolParts(sp[0], sp[1]);
      }
    }

    // Fallback: از currency به عنوان base و پیش‌فرض quote = usdt
    // اگر پروژه‌ی شما field جدا برای quote دارد، همان را جایگزین کنید.
    final String base = cx.getCurrency().getSymbol().toLowerCase(Locale.ROOT);
    final String quote = "usdt";

    return new SymbolParts(base, quote);
  }

  private @Nullable BigDecimal bestPrice(String base, String quote, OrderSide orderSide) {
    try {
      final StatsResponse resp =
          nobitexPublicRestClient
              .get()
              .uri(
                  (UriBuilder b) ->
                      b.path(PATH_STATS)
                          .queryParam("srcCurrency", lower(base))
                          .queryParam("dstCurrency", lower(quote))
                          .build())
              .retrieve()
              .body(StatsResponse.class);

      if (resp == null || resp.stats() == null || resp.stats().isEmpty()) {
        log.debug("Empty stats from Nobitex for {}-{}", base, quote);
        return null;
      }

      final String key = (base + "-" + quote).toLowerCase(Locale.ROOT);
      MarketStat s = resp.stats().get(key);
      if (s == null) {
        s =
            resp.stats().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (s == null) {
          log.debug("No stat entry found for key {}", key);
          return null;
        }
      }

      final String priceStr = (orderSide == OrderSide.SELL) ? s.bestSell() : s.bestBuy();
      return StringUtils.hasText(priceStr) ? new BigDecimal(priceStr) : null;

    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} /market/stats {}-{} side={} body={}",
          http.getRawStatusCode(),
          base,
          quote,
          orderSide,
          safeBody(http));
      return null;
    } catch (Exception ex) {
      log.error("Error /market/stats {}-{} side={}: {}", base, quote, orderSide, ex.toString());
      return null;
    }
  }

  private static String defaultClientOrderId(OrderRequest req) {
    return (req.getSide() + "-" + req.getSymbol() + "-" + System.currentTimeMillis())
        .toUpperCase(Locale.ROOT);
  }

  private static String safeBody(RestClientResponseException e) {
    try {
      return e.getResponseBodyAsString();
    } catch (Exception ignored) {
      return "<unavailable>";
    }
  }

  private static String lower(String v) {
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }

  private static String requireNonBlankLower(String v, String field) {
    if (!StringUtils.hasText(v)) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return v.toLowerCase(Locale.ROOT);
  }

  private static BigDecimal requirePositive(BigDecimal v, String field) {
    if (v == null || v.signum() <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return v;
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    return a != null && a.equalsIgnoreCase(b);
  }

  private static String asString(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
