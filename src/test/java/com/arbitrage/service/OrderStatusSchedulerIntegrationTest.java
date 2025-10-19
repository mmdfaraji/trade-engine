package com.arbitrage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arbitrage.dal.OrderService;
import com.arbitrage.entities.Balance;
import com.arbitrage.entities.BalanceLock;
import com.arbitrage.entities.Currency;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.entities.Order;
import com.arbitrage.entities.Pair;
import com.arbitrage.enums.ExchangeStatus;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.enums.TimeInForce;
import com.arbitrage.model.ExchangeOrderStatus;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.model.Quote;
import com.arbitrage.respository.BalanceLockRepository;
import com.arbitrage.respository.BalanceRepository;
import com.arbitrage.respository.CurrencyRepository;
import com.arbitrage.respository.ExchangeAccountRepository;
import com.arbitrage.respository.ExchangeRepository;
import com.arbitrage.respository.OrderRepository;
import com.arbitrage.respository.PairRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OrderStatusScheduler.class,
  OrderService.class,
  OrderStatusSchedulerIntegrationTest.TestConfig.class
})
@ActiveProfiles("test")
class OrderStatusSchedulerIntegrationTest {

  private static final String ORDER_LOCK_REASON = "ORDER_SUBMIT";

  @Autowired private OrderStatusScheduler scheduler;
  @Autowired private OrderRepository orderRepository;
  @Autowired private BalanceRepository balanceRepository;
  @Autowired private BalanceLockRepository balanceLockRepository;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private PairRepository pairRepository;
  @Autowired private ExchangeRepository exchangeRepository;
  @Autowired private ExchangeAccountRepository exchangeAccountRepository;
  @Autowired private StubExchangeMarketClient stubExchangeMarketClient;

  private Exchange exchange;
  private ExchangeAccount account;
  private Currency baseCurrency;
  private Currency quoteCurrency;
  private Pair pair;

  @BeforeEach
  void setUp() {
    stubExchangeMarketClient.reset();
    ReflectionTestUtils.setField(scheduler, "orderTimeout", Duration.ofMinutes(10));

    baseCurrency =
        currencyRepository.save(Currency.builder().symbol("BTC").name("Bitcoin").build());
    quoteCurrency =
        currencyRepository.save(Currency.builder().symbol("USDT").name("Tether").build());
    pair =
        pairRepository.save(
            Pair.builder()
                .symbol("BTC-USDT")
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .build());

    exchange = new Exchange();
    exchange.setName("TestEx");
    exchange.setStatus(ExchangeStatus.ACTIVE);
    exchange = exchangeRepository.save(exchange);

    account =
        exchangeAccountRepository.save(
            ExchangeAccount.builder()
                .exchange(exchange)
                .label("TestEx")
                .apiKey("api-key")
                .secretKey("secret")
                .isPrimary(true)
                .build());
  }

  @Test
  void refreshSentOrdersStatus_updatesOrderAndBalances_forPartialFill() {
    Balance balance =
        balanceRepository.save(
            Balance.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .available(BigDecimal.ZERO)
                .reserved(new BigDecimal("20"))
                .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .exchange(exchange)
                .exchangeAccount(account)
                .pair(pair)
                .side(OrderSide.BUY.name())
                .type("LIMIT")
                .tif(TimeInForce.IOC.name())
                .clientOrderId("client-partial")
                .exchangeOrderId("exchange-partial")
                .price(new BigDecimal("10"))
                .qty(new BigDecimal("2"))
                .qtyExec(BigDecimal.ZERO)
                .notional(new BigDecimal("20"))
                .status(OrderStatus.SENT)
                .filledQty(BigDecimal.ZERO)
                .avgPrice(BigDecimal.ZERO)
                .sentAt(Date.from(Instant.now()))
                .build());

    BalanceLock lock =
        balanceLockRepository.save(
            BalanceLock.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .amount(new BigDecimal("20"))
                .reason(ORDER_LOCK_REASON)
                .signalId(String.valueOf(order.getId()))
                .build());

    ExchangeOrderStatus exchangeStatus =
        ExchangeOrderStatus.of(OrderStatus.NEW)
            .withFilledQuantity(new BigDecimal("1"))
            .withAveragePrice(new BigDecimal("10"))
            .withExecutedNotional(new BigDecimal("10"));
    stubExchangeMarketClient.stubStatus(order.getExchangeOrderId(), exchangeStatus);

    scheduler.refreshSentOrdersStatus();

    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(OrderStatus.PARTIAL);
    assertThat(updated.getQtyExec()).isEqualByComparingTo("1");
    assertThat(updated.getFilledQty()).isEqualByComparingTo("1");
    assertThat(updated.getAvgPrice()).isEqualByComparingTo("10");
    assertThat(updated.getClosedAt()).isNull();

    BalanceLock updatedLock = balanceLockRepository.findById(lock.getId()).orElseThrow();
    assertThat(updatedLock.getAmount()).isEqualByComparingTo("10");

    Balance updatedBalance = balanceRepository.findById(balance.getId()).orElseThrow();
    assertThat(updatedBalance.getReserved()).isEqualByComparingTo("10");
    assertThat(updatedBalance.getAvailable()).isEqualByComparingTo("0");

    assertThat(stubExchangeMarketClient.wasCancelInvoked(order.getExchangeOrderId())).isFalse();
  }

  @Test
  void refreshSentOrdersStatus_marksOrderFilled_andClearsLock() {
    Balance balance =
        balanceRepository.save(
            Balance.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .available(BigDecimal.ZERO)
                .reserved(new BigDecimal("50"))
                .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .exchange(exchange)
                .exchangeAccount(account)
                .pair(pair)
                .side(OrderSide.BUY.name())
                .type("LIMIT")
                .tif(TimeInForce.IOC.name())
                .clientOrderId("client-filled")
                .exchangeOrderId("exchange-filled")
                .price(new BigDecimal("10"))
                .qty(new BigDecimal("5"))
                .qtyExec(BigDecimal.ZERO)
                .notional(new BigDecimal("50"))
                .status(OrderStatus.SENT)
                .filledQty(BigDecimal.ZERO)
                .avgPrice(BigDecimal.ZERO)
                .sentAt(Date.from(Instant.now()))
                .build());

    BalanceLock lock =
        balanceLockRepository.save(
            BalanceLock.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .amount(new BigDecimal("50"))
                .reason(ORDER_LOCK_REASON)
                .signalId(String.valueOf(order.getId()))
                .build());

    ExchangeOrderStatus exchangeStatus =
        ExchangeOrderStatus.of(OrderStatus.FILLED)
            .withFilledQuantity(new BigDecimal("5"))
            .withAveragePrice(new BigDecimal("10"))
            .withExecutedNotional(new BigDecimal("50"));
    stubExchangeMarketClient.stubStatus(order.getExchangeOrderId(), exchangeStatus);

    scheduler.refreshSentOrdersStatus();

    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(updated.getQtyExec()).isEqualByComparingTo("5");
    assertThat(updated.getFilledQty()).isEqualByComparingTo("5");
    assertThat(updated.getAvgPrice()).isEqualByComparingTo("10");
    assertThat(updated.getClosedAt()).isNotNull();

    BalanceLock updatedLock = balanceLockRepository.findById(lock.getId()).orElseThrow();
    assertThat(updatedLock.getAmount()).isEqualByComparingTo("0");

    Balance updatedBalance = balanceRepository.findById(balance.getId()).orElseThrow();
    assertThat(updatedBalance.getReserved()).isEqualByComparingTo("0");
    assertThat(updatedBalance.getAvailable()).isEqualByComparingTo("0");
  }

  @Test
  void refreshSentOrdersStatus_cancelsTimedOutOrder_andReleasesBalance() {
    ReflectionTestUtils.setField(scheduler, "orderTimeout", Duration.ofSeconds(1));

    Balance balance =
        balanceRepository.save(
            Balance.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .available(BigDecimal.ZERO)
                .reserved(new BigDecimal("40"))
                .build());

    Order order =
        orderRepository.save(
            Order.builder()
                .exchange(exchange)
                .exchangeAccount(account)
                .pair(pair)
                .side(OrderSide.BUY.name())
                .type("LIMIT")
                .tif(TimeInForce.IOC.name())
                .clientOrderId("client-cancel")
                .exchangeOrderId("exchange-cancel")
                .price(new BigDecimal("10"))
                .qty(new BigDecimal("4"))
                .qtyExec(BigDecimal.ZERO)
                .notional(new BigDecimal("40"))
                .status(OrderStatus.SENT)
                .filledQty(BigDecimal.ZERO)
                .avgPrice(BigDecimal.ZERO)
                .sentAt(Date.from(Instant.now().minusSeconds(120)))
                .build());

    BalanceLock lock =
        balanceLockRepository.save(
            BalanceLock.builder()
                .exchangeAccount(account)
                .currency(quoteCurrency)
                .amount(new BigDecimal("40"))
                .reason(ORDER_LOCK_REASON)
                .signalId(String.valueOf(order.getId()))
                .build());

    stubExchangeMarketClient.stubStatus(
        order.getExchangeOrderId(), ExchangeOrderStatus.of(OrderStatus.NEW));
    stubExchangeMarketClient.stubCancelResponse(order.getExchangeOrderId(), true);

    scheduler.refreshSentOrdersStatus();

    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(updated.getClosedAt()).isNotNull();
    assertThat(updated.getQtyExec()).isEqualByComparingTo("0");
    assertThat(updated.getFilledQty()).isEqualByComparingTo("0");

    BalanceLock updatedLock = balanceLockRepository.findById(lock.getId()).orElseThrow();
    assertThat(updatedLock.getAmount()).isEqualByComparingTo("0");

    Balance updatedBalance = balanceRepository.findById(balance.getId()).orElseThrow();
    assertThat(updatedBalance.getReserved()).isEqualByComparingTo("0");
    assertThat(updatedBalance.getAvailable()).isEqualByComparingTo("40");

    assertThat(stubExchangeMarketClient.wasCancelInvoked(order.getExchangeOrderId())).isTrue();
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    StubExchangeMarketClient stubExchangeMarketClient() {
      return new StubExchangeMarketClient();
    }

    @Bean
    ExchangeClientFactory exchangeClientFactory(StubExchangeMarketClient stub) {
      return new ExchangeClientFactory(List.of(stub));
    }
  }

  static class StubExchangeMarketClient implements ExchangeMarketClient {

    private final Set<String> cancelledOrders = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Boolean> cancelResponses = new ConcurrentHashMap<>();
    private final java.util.Map<String, ExchangeOrderStatus> statuses = new ConcurrentHashMap<>();

    @Override
    public String getExchangeName() {
      return "TestEx";
    }

    @Override
    public BigDecimal getWalletBalance(String currency) {
      return BigDecimal.ZERO;
    }

    @Override
    public List<Quote> getQuotes() {
      return Collections.emptyList();
    }

    @Override
    public OrderAck submitOrder(OrderRequest orderRequest) {
      return null;
    }

    @Override
    public boolean cancelOrder(String orderId) {
      cancelledOrders.add(orderId);
      return cancelResponses.getOrDefault(orderId, false);
    }

    @Override
    public ExchangeOrderStatus getOrderStatus(String orderId) {
      return statuses.get(orderId);
    }

    void stubStatus(String orderId, ExchangeOrderStatus status) {
      statuses.put(orderId, status);
    }

    void stubCancelResponse(String orderId, boolean result) {
      cancelResponses.put(orderId, result);
    }

    boolean wasCancelInvoked(String orderId) {
      return cancelledOrders.contains(orderId);
    }

    void reset() {
      cancelledOrders.clear();
      cancelResponses.clear();
      statuses.clear();
    }
  }
}
