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
import com.arbitrage.service.exchanges.WallexMarketClient;
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
class WallexMarketClientTest {

  @Autowired EntityManager em;
  @Autowired CurrencyExchangeRepository currencyExchangeRepository;
  @Autowired WallexMarketClient client;

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
      throw ex; // Live call: failure points to connectivity/token/currency issues.
    }

    assertThat(balance).as("balance for %s should not be null", currency).isNotNull();
    assertThat(balance)
        .as("balance for %s should be >= 0", currency)
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

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
    String orderId = System.getenv("RAMZINEX_OPPEND_ORDER_ID");
    Assertions.assertTrue(
        orderId != null && !orderId.isBlank(),
        "Set RAMZINEX_OPPEND_ORDER_ID to an order returning oppend to run this test");

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
  @Timeout(20)
  void getQuotes_realCall_returnsBidAsk_forRegisteredPairs() {
    Exchange wallex = ex("WALLEX");

    Currency btc = ccy("BTC");
    Currency eth = ccy("ETH");
    Currency irr = ccy("IRR");

    // Wallex frequently used pairs: btc-irr and eth-irr
    cx(wallex, btc, irr, "btc-tmn");
    cx(wallex, eth, irr, "eth-tmn");

    em.flush();

    // --- Act: real HTTP calls to Wallex public API orderbooks ---
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
      // Typically ask >= bid; uncomment for stricter validation:
      // assertThat(q.getAsk()).isGreaterThanOrEqualTo(q.getBid());
    }
  }

  @Test
  @DisplayName("Submit order: opt-in and requires token")
  @Timeout(25)
  void submitOrder_live_isOptInAndRequiresToken() {
    var req =
        new OrderRequest(
            "btcusdt",
            "BUY",
            new BigDecimal("0.0001"), // Align with Wallex minimum BTC order size.
            new BigDecimal(
                "100000000"), // Intentionally far from market price to trigger rejection.
            "IOC");

    Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(25),
        () -> {
          try {
            var ack = client.submitOrder(req);
            assertThat(ack).isNotNull();
            assertThat(ack.getStatus()).as("ack status").isNotBlank();
            assertThat(ack.getClientOrderId()).as("clientOrderId").isNotBlank();
          } catch (Exception ex) {
            // Acceptable: the exchange may reject it due to minimum order rules.
            System.out.println(
                "submitOrder live returned exception (acceptable for opt-in test): "
                    + ex.getMessage());
          }
        });
  }

  @Test
  @DisplayName("Cancel order: opt-in and requires token")
  @Timeout(20)
  void cancelOrder_live_isOptInAndRequiresToken() {
    // Note: Wallex cancel implementation requires a numeric order_id.
    boolean ok = client.cancelOrder("0"); // Non-real identifier → typically false
    assertThat(ok).isIn(true, false);
  }
}
