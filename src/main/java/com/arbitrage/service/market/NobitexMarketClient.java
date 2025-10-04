package com.arbitrage.service.market;

import com.arbitrage.entities.CurrencyExchange;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  // ---------- DTO های پاسخ عمومی ----------
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OrdersListResponse(
      @JsonProperty("status") String status, @JsonProperty("orders") List<OrderEntry> orders) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OrderEntry(@JsonProperty("price") String price) {}

  private record BalanceResponse(String balance, String status) {}

  @Override
  public BigDecimal getWalletBalance(String currency) {
    String ccy = currency.toLowerCase(Locale.ROOT);

    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("currency", ccy);

      BalanceResponse resp =
          nobitexPrivateRestClient
              .post()
              .uri("/users/wallets/balance")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED) // 👈 فرم‌اِنکود
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(BalanceResponse.class);

      if (resp == null || resp.balance() == null || resp.status() == null) {
        throw new IllegalStateException("Empty response from Nobitex balance API");
      }

      return new BigDecimal(resp.balance());

    } catch (RestClientResponseException httpEx) {
      log.error(
          "Nobitex balance HTTP {}: {}",
          httpEx.getRawStatusCode(),
          httpEx.getResponseBodyAsString());
      throw httpEx;
    } catch (Exception ex) {
      log.error("Nobitex balance error for {}: {}", ccy, ex.toString());
      throw new IllegalStateException("Failed to fetch wallet balance for " + ccy, ex);
    }
  }

  @Override
  public List<Quote> getQuotes() {
    var entries = currencyExchangeRepo.findByExchange_Name(exchangeName);
    if (entries == null || entries.isEmpty()) {
      log.info("No symbols for exchange '{}'", exchangeName);
      return List.of();
    }

    long ts = Instant.now().toEpochMilli();
    List<Quote> out = new ArrayList<>(entries.size());

    for (CurrencyExchange cx : entries) {
      var parts = symbolParts(cx);
      String base = parts.base();
      String quote = parts.quote();

      BigDecimal ask = bestPrice(base, quote, Side.SELL); // کمترین فروشنده
      BigDecimal bid = bestPrice(base, quote, Side.BUY); // بیشترین خریدار

      if (ask != null && bid != null) {
        out.add(new Quote(base + "-" + quote, bid, ask, ts));
      } else {
        log.warn("Skip {}-{}: ask={} bid={}", base, quote, ask, bid);
      }
    }

    return out;
  }

  private enum Side {
    BUY,
    SELL
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record StatsResponse(
      @JsonProperty("status") String status,
      @JsonProperty("stats") Map<String, MarketStat> stats) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MarketStat(
      @JsonProperty("bestSell") String bestSell, // ask
      @JsonProperty("bestBuy") String bestBuy // bid
      ) {}

  private @Nullable BigDecimal bestPrice(String base, String quote, Side side) {
    try {
      StatsResponse resp =
          nobitexPublicRestClient
              .get()
              .uri(
                  (UriBuilder b) ->
                      b.path("/market/stats")
                          .queryParam("srcCurrency", base.toLowerCase())
                          .queryParam("dstCurrency", quote.toLowerCase())
                          .build())
              .retrieve()
              .body(StatsResponse.class);

      if (resp == null || resp.stats() == null || resp.stats().isEmpty()) {
        log.debug("Empty stats from Nobitex for {}-{}", base, quote);
        return null;
      }

      // key format: "btc-usdt" / "btc-rls" ...
      String key = (base + "-" + quote).toLowerCase();
      MarketStat s = resp.stats().get(key);
      if (s == null) {
        // some backends may return uppercase keys; try a relaxed search
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

      String priceStr = (side == Side.SELL) ? s.bestSell() : s.bestBuy();
      if (priceStr == null || priceStr.isBlank()) {
        return null;
      }
      return new BigDecimal(priceStr);

    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} /market/stats {}-{} side={} body={}",
          http.getRawStatusCode(),
          base,
          quote,
          side,
          safeBody(http));
      return null;
    } catch (Exception ex) {
      log.error("Error /market/stats {}-{} side={}: {}", base, quote, side, ex.toString());
      return null;
    }
  }

  private static String safeBody(RestClientResponseException e) {
    try {
      return e.getResponseBodyAsString();
    } catch (Exception ignored) {
      return "<unavailable>";
    }
  }

  @Override
  public OrderAck submitOrder(OrderRequest orderRequest) {
    try {
      var body =
          java.util.Map.<String, Object>of(
              "type", "limit",
              "side", orderRequest.getSide().toLowerCase(Locale.ROOT),
              "symbol", orderRequest.getSymbol().toLowerCase(Locale.ROOT), // مثل "btc-usdt"
              "price", orderRequest.getPrice(),
              "quantity", orderRequest.getQty(),
              "timeInForce", orderRequest.getTif());

      var resp =
          nobitexPrivateRestClient
              .post()
              .uri("/market/orders/add")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(java.util.Map.class);

      String clientOrderId =
          resp != null ? (String) resp.getOrDefault("clientOrderId", null) : null;
      String exchangeOrderId = resp != null ? (String) resp.getOrDefault("orderId", null) : null;
      String status = resp != null ? (String) resp.getOrDefault("status", "unknown") : "unknown";

      return new OrderAck(
          clientOrderId != null ? clientOrderId : defaultClientOrderId(orderRequest),
          exchangeOrderId,
          status);

    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} placeOrder symbol={} body={}",
          http.getRawStatusCode(),
          orderRequest.getSymbol(),
          safeBody(http));
      throw http;
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    try {
      var resp =
          nobitexPrivateRestClient
              .post()
              .uri("/market/orders/cancel")
              .contentType(MediaType.APPLICATION_JSON)
              .body(java.util.Map.of("clientOrderId", orderId))
              .retrieve()
              .body(java.util.Map.class);

      String status = resp != null ? (String) resp.getOrDefault("status", "unknown") : "unknown";
      return "ok".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status);
    } catch (RestClientResponseException http) {
      log.error(
          "HTTP {} cancelOrder clientOrderId={} body={}",
          http.getRawStatusCode(),
          orderId,
          safeBody(http));
      return false;
    } catch (Exception ex) {
      log.error("Error cancelOrder clientOrderId={}: {}", orderId, ex.toString());
      return false;
    }
  }

  private record SymbolParts(String base, String quote) {}

  /**
   * اگر exchangeSymbol مثل "btc-usdt" ست بود، همان را می‌شکند؛ وگرنه از base/quote موجودیت استفاده
   * می‌کند.
   */
  private SymbolParts symbolParts(CurrencyExchange cx) {
    String exSymbol = cx.getExchangeSymbol();
    if (StringUtils.hasText(exSymbol) && exSymbol.contains("-")) {
      var sp = exSymbol.toLowerCase(Locale.ROOT).split("-");
      if (sp.length == 2 && StringUtils.hasText(sp[0]) && StringUtils.hasText(sp[1])) {
        return new SymbolParts(sp[0], sp[1]);
      }
    }
    String base = cx.getCurrency().getSymbol().toUpperCase(Locale.ROOT);
    String quote = "USDT"; // cx.getCurrencyQuote().getSymbol().toLowerCase(Locale.ROOT);
    return new SymbolParts(base, quote);
  }

  private static String defaultClientOrderId(OrderRequest req) {
    return "CLI-" + req.getSymbol() + "-" + System.currentTimeMillis();
  }
}
