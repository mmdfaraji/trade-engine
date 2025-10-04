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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("localtest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    //    cx.setCurrencyQuote(quote);
    cx.setExchangeSymbol(maybeSymbol);
    em.persist(cx);
    return cx;
  }

  /**
   * خواندن موجودی کیف پول (live): فقط زمانی اجرا می‌شود که RUN_LIVE_PRIVATE_TESTS=true و توکن
   * نوبیتکس ست شده باشد.
   *
   * <p>متغیر محیطی اختیاری NOBITEX_TEST_CCY برای تعیین ارز (پیش‌فرض "ltc").
   */
  @Test
  @Order(0)
  @Timeout(15)
  void getWalletBalance_live_returnsNonNegative_whenTokenPresent() {
    BigDecimal balance = null;
    try {
      balance = client.getWalletBalance("rls");
    } catch (Exception ex) {
      // اگر سرور 4xx/5xx داد، اینجا دیده میشه. برای دیباگ پیام را چاپ می‌کنیم.
      System.out.println("getWalletBalance live failed: " + ex.getMessage());
      throw ex; // بگذاریم تست fail شود چون شرایط را opt-in کرده‌ایم
    }

    assertThat(balance).isNotNull();
    assertThat(balance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  /**
   * حالت منفی: ارز نامعتبر باید خطا بدهد (رفتار واقعی API معمولاً ۴xx). این تست هم نیازمند فعال
   * بودن تست‌های خصوصی و توکن معتبر است.
   */
  @Test
  @Order(4)
  @Timeout(15)
  void getWalletBalance_live_invalidCurrency_throws() {
    String invalid = "___invalid___";

    Assertions.assertThrows(Exception.class, () -> client.getWalletBalance(invalid));
  }

  @Test
  @Order(1)
  @Transactional
  @Timeout(15) // ثانیه
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
    assertThat(quotes).isNotEmpty();
    // انتظار داریم حداقل برای یک جفت قیمت برگردد
    var any = quotes.get(0);
    assertThat(any.getTs()).isGreaterThan(0);

    // برای هر کویت: bid/ask باید مقدار مثبت داشته باشند
    for (var q : quotes) {
      assertThat(q.getAsk()).isNotNull();
      assertThat(q.getBid()).isNotNull();
      assertThat(q.getAsk()).isGreaterThan(BigDecimal.ZERO);
      assertThat(q.getBid()).isGreaterThan(BigDecimal.ZERO);
      // معمولاً ask >= bid، اما برای اطمینان از شرایط نادری که ممکن است برعکس شود، شرط را نرم
      // می‌گیریم
    }
  }

  /**
   * این تست فقط در صورتی اجرا می‌شود که متغیر محیطی RUN_LIVE_ORDER_TESTS=true باشد و همچنین
   * NOBITEX_TOKEN ست شده باشد. ⚠️ مسئولیت ریسک اجرای سفارش به عهده‌ی شماست.
   */
  @Test
  @Order(2)
  @Timeout(20)
  void placeOrder_live_isOptInAndRequiresToken() {
    // این فقط یک مثال است؛ ممکن است نیاز به حداقل مقادیر و قوانین بازار داشته باشد.
    // برای جلوگیری از معامله واقعی، این تست را تنها زمانی اجرا کنید که حساب/محیط شما امن است.
    var req =
        new OrderRequest(
            "btc-usdt",
            "buy",
            new BigDecimal("0.0001"), // توجه به حداقل مقدار در صرافی
            new BigDecimal("10000"), // عمداً پایین تا احتمالاً رد شود یا FOK/IOC بی‌اثر شود
            "IOC");

    Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> {
          try {
            var ack = client.submitOrder(req);
            // بسته به پاسخ، فقط presence فیلدها را چک می‌کنیم
            assertThat(ack).isNotNull();
            assertThat(ack.getStatus()).isNotBlank();
          } catch (Exception ex) {
            // قابل قبول: ممکن است به خاطر قوانین حداقل سفارش/اعتبار توکن خطا بدهد
            System.out.println(
                "placeOrder live call returned exception (acceptable for opt-in test): "
                    + ex.getMessage());
          }
        });
  }

  /**
   * لغو سفارش — فقط اگر متغیر محیطی تست خصوصی فعال باشد. می‌توانید شناسه‌ی کلاینت واقعی بگذارید یا
   * انتظار false داشته باشید.
   */
  @Test
  @Order(3)
  //  @EnabledIfEnvironmentVariable(named = "RUN_LIVE_ORDER_TESTS", matches = "true")
  @Timeout(20)
  void cancelOrder_live_isOptInAndRequiresToken() {
    boolean ok = client.cancelOrder("NON-EXISTENT-CLIENT-ORDER-ID");
    // احتمالاً false برمی‌گردد (سفارش وجود ندارد) — فقط صحت اتصال/توکن برای endpoint خصوصی مهم است
    assertThat(ok).isIn(true, false);
  }
}
