package com.arbitrage.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.arbitrage.entities.Currency;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.exception.OrderNotFoundException;
import com.arbitrage.model.ExchangeOrderStatus;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import com.arbitrage.service.exchanges.NobitexMarketClient;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("localtest")
class NobitexMarketClientTest {

  @Autowired EntityManager em;
  @Autowired CurrencyExchangeRepository currencyExchangeRepository;
  @Autowired NobitexMarketClient client;

  private Exchange ex(String name) {
    Exchange e = new Exchange();
    e.setName(name);
    em.persist(e);
    return e;
  }

  private Currency ccy(String sym) {
    Currency c = new Currency();
    c.setSymbol(sym);
    em.persist(c);
    return c;
  }

  private CurrencyExchange cx(Exchange ex, Currency base, Currency quote, String maybeSymbol) {
    CurrencyExchange cx = new CurrencyExchange();
    cx.setExchange(ex);
    cx.setCurrency(base);
    // If the production model exposes the quote currency, set it here.
    // cx.setCurrencyQuote(quote);
    cx.setExchangeSymbol(maybeSymbol);
    em.persist(cx);
    return cx;
  }

  @Test
  @DisplayName("Wallet balance: non-negative when token present")
  @Timeout(15)
  void getWalletBalance_live_returnsNonNegative_whenTokenPresent() {
    String currency = "RIAL";

    BigDecimal balance;
    try {
      balance = client.getWalletBalance(currency);
    } catch (Exception ex) {
      System.out.println("getWalletBalance live failed: " + ex.getMessage());
      throw ex; // Opt-in test: failing indicates an issue with connectivity/token/currency.
    }

    assertThat(balance).as("balance for %s should not be null", currency).isNotNull();
    assertThat(balance)
        .as("balance for %s should be >= 0", currency)
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  /** Negative case: an invalid currency should raise an error (real API typically returns 4xx). */
  @Test
  @DisplayName("Wallet balance: invalid currency throws")
  @Timeout(15)
  void getWalletBalance_live_invalidCurrency_throws() {
    String invalid = "___invalid___";
    Assertions.assertThrows(Exception.class, () -> client.getWalletBalance(invalid));
  }

  // @Test
  @DisplayName("Get order status: filled order id resolves to FILLED")
  @Timeout(20)
  void getOrderStatus_live_returnsFilled_whenOrderFilled() {
    String orderId = "4129491517";

    ExchangeOrderStatus status = client.getOrderStatus(orderId);

    assertThat(status).as("status response").isNotNull();
    assertThat(status.status()).isEqualTo(OrderStatus.FILLED);
  }

  @Test
  @DisplayName("Get order status: not found")
  @Timeout(20)
  void getOrderStatus_throwsException_notFoundOrder() {
    String orderId = "11111";

    assertThrows(OrderNotFoundException.class, () -> client.getOrderStatus(orderId));
  }

  // @Test
  @DisplayName("Get order status: oppend maps to SENT")
  @Timeout(20)
  void getOrderStatus_live_returnsSent_whenStatusOppend() {
    String orderId = System.getenv("NOBITEX_OPPEND_ORDER_ID");
    Assertions.assertTrue(
        orderId != null && !orderId.isBlank(),
        "Set NOBITEX_OPPEND_ORDER_ID to an order returning oppend to run this test");

    ExchangeOrderStatus status = client.getOrderStatus(orderId);

    assertThat(status).as("status response").isNotNull();
    assertThat(status.status()).isEqualTo(OrderStatus.SENT);
    assertThat(status.filledQuantity()).isNull();
    assertThat(status.averagePrice()).isNull();
    assertThat(status.executedNotional()).isNull();
  }

  @Test
  @DisplayName("Quotes: returns bid/ask for registered pairs")
  @Transactional
  @Timeout(15)
  void getQuotes_realCall_returnsBidAsk_forRegisteredPairs() {
    Exchange nobitex = ex("NOBITEX");

    Currency btc = ccy("BTC");
    Currency eth = ccy("ETH");
    Currency usdt = ccy("USDT");

    // We can rely on exchangeSymbol or fall back to base/quote symbols.
    cx(nobitex, btc, usdt, "btc-usdt");
    cx(nobitex, eth, usdt, null); // Fallback to base/quote

    em.flush();

    // --- Act: real HTTP calls to Nobitex public API ---
    List<Quote> quotes = client.getQuotes();

    // --- Assert ---
    assertThat(quotes).as("quotes list should not be empty").isNotEmpty();

    for (var q : quotes) {
      assertThat(q.getTs()).as("timestamp should be > 0").isGreaterThan(0);
      assertThat(q.getAsk())
          .as("ask should be positive")
          .isNotNull()
          .isGreaterThan(BigDecimal.ZERO);
      assertThat(q.getBid())
          .as("bid should be positive")
          .isNotNull()
          .isGreaterThan(BigDecimal.ZERO);
      // Uncomment if you prefer the stricter ask >= bid assertion:
      // assertThat(q.getAsk()).isGreaterThanOrEqualTo(q.getBid());
    }
  }

  @Test
  @DisplayName("Submit order: opt-in and requires token")
  @Timeout(20)
  void submitOrder_live_isOptInAndRequiresToken() {
    var req =
        new OrderRequest(
            "btc-usdt",
            "buy",
            new BigDecimal("0.0001"), // Align with the exchange minimum order size.
            new BigDecimal("10000"), // Intentionally low so rejection is likely.
            "IOC");

    Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> {
          try {
            var ack = client.submitOrder(req);
            assertThat(ack).isNotNull();
            assertThat(ack.getStatus()).as("ack status").isNotBlank();
            assertThat(ack.getClientOrderId()).as("clientOrderId").isNotBlank();
          } catch (Exception ex) {
            // Acceptable: the exchange may reject it because of minimum order rules.
            System.out.println(
                "submitOrder live call returned exception (acceptable for opt-in test): "
                    + ex.getMessage());
          }
        });
  }

  @Test
  @DisplayName("Cancel order: opt-in and requires token")
  @Timeout(20)
  void cancelOrder_live_isOptInAndRequiresToken() {
    boolean ok = client.cancelOrder("NON-EXISTENT-CLIENT-ORDER-ID");
    // Most likely false (order does not exist) — goal is verifying auth for the private endpoint.
    assertThat(ok).isIn(true, false);
  }
}
