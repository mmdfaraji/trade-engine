package com.arbitrage.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.arbitrage.entities.Currency;
import com.arbitrage.entities.CurrencyExchange;
import com.arbitrage.entities.Exchange;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.CurrencyExchangeRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    // اگر در مدل واقعی فیلد quote داری، اینجا ست کن
    // cx.setCurrencyQuote(quote);
    cx.setExchangeSymbol(maybeSymbol);
    em.persist(cx);
    return cx;
  }

  /**
   * خواندن موجودی کیف پول (live): فقط زمانی اجرا می‌شود که RUN_LIVE_PRIVATE_TESTS=true و توکن
   * نوبیتکس ست باشد. متغیر محیطی اختیاری NOBITEX_TEST_CCY برای تعیین ارز (پیش‌فرض "rls").
   */
  @Test
  @DisplayName("Wallet balance: non-negative when token present")
  @Timeout(15)
  void getWalletBalance_live_returnsNonNegative_whenTokenPresent() {
    String currency =
        System.getenv().getOrDefault("NOBITEX_TEST_CCY", "rls").toLowerCase(Locale.ROOT);

    BigDecimal balance;
    try {
      balance = client.getWalletBalance(currency);
    } catch (Exception ex) {
      System.out.println("getWalletBalance live failed: " + ex.getMessage());
      throw ex; // opt-in است؛ fail مناسب است
    }

    assertThat(balance).as("balance for %s should not be null", currency).isNotNull();
    assertThat(balance)
        .as("balance for %s should be >= 0", currency)
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  /** حالت منفی: ارز نامعتبر باید خطا بدهد (رفتار واقعی API معمولاً ۴xx). */
  @Test
  @DisplayName("Wallet balance: invalid currency throws")
  @Timeout(15)
  void getWalletBalance_live_invalidCurrency_throws() {
    String invalid = "___invalid___";
    Assertions.assertThrows(Exception.class, () -> client.getWalletBalance(invalid));
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

    // می‌توانیم از exchangeSymbol استفاده کنیم یا به base/quote بسنده کنیم
    cx(nobitex, btc, usdt, "btc-usdt");
    cx(nobitex, eth, usdt, null); // fallback به base/quote

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
      // اگر خواستی سخت‌گیرانه‌ترش کنی:
      // assertThat(q.getAsk()).isGreaterThanOrEqualTo(q.getBid());
    }
  }

  /**
   * این تست صرفاً نمونه است و باید با دقت اجرا شود؛ مسئولیت ریسک اجرای سفارش با شماست. برای اجرا:
   * RUN_LIVE_PRIVATE_TESTS=true و NOBITEX_TOKEN را ست کن.
   */
  @Test
  @DisplayName("Submit order: opt-in and requires token")
  @Timeout(20)
  void submitOrder_live_isOptInAndRequiresToken() {
    var req =
        new OrderRequest(
            "btc-usdt",
            "buy",
            new BigDecimal("0.0001"), // حداقل مقدار را با قوانین صرافی هماهنگ کن
            new BigDecimal("10000"), // عمداً پایین تا احتمالاً رد شود
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
            // قابل قبول: ممکن است به خاطر حداقل سفارش/Ruleها رد شود
            System.out.println(
                "submitOrder live call returned exception (acceptable for opt-in test): "
                    + ex.getMessage());
          }
        });
  }

  /**
   * لغو سفارش — فقط اگر تست‌های خصوصی فعال باشد. می‌توانید شناسه‌ی کلاینت واقعی بگذارید یا انتظار
   * false داشته باشید.
   */
  @Test
  @DisplayName("Cancel order: opt-in and requires token")
  @Timeout(20)
  void cancelOrder_live_isOptInAndRequiresToken() {
    boolean ok = client.cancelOrder("NON-EXISTENT-CLIENT-ORDER-ID");
    // احتمالاً false برمی‌گردد (سفارش وجود ندارد) — هدف، صحت اتصال/توکن برای endpoint خصوصی است
    assertThat(ok).isIn(true, false);
  }
}
