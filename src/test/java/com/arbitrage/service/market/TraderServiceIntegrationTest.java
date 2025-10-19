package com.arbitrage.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.arbitrage.dal.OrderService;
import com.arbitrage.dto.DecimalValueDto;
import com.arbitrage.dto.OrderInstructionDto;
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
import com.arbitrage.service.ExchangeAccessService;
import com.arbitrage.service.ExchangeClientFactory;
import com.arbitrage.service.ExchangeMarketClient;
import com.arbitrage.service.TraderService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TraderService.class,
  ExchangeAccessService.class,
  OrderService.class,
  TraderServiceIntegrationTest.TraderServiceIntegrationTestConfig.class
})
@ActiveProfiles("test")
class TraderServiceIntegrationTest {

  @Autowired private TraderService traderService;
  @Autowired private PairRepository pairRepository;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private ExchangeRepository exchangeRepository;
  @Autowired private ExchangeAccountRepository exchangeAccountRepository;
  @Autowired private BalanceRepository balanceRepository;
  @Autowired private BalanceLockRepository balanceLockRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private StubExchangeMarketClient stubExchangeMarketClient;

  private Exchange exchange;
  private ExchangeAccount account;
  private Currency quoteCurrency;
  private Pair pair;

  @BeforeEach
  void setUp() {
    quoteCurrency =
        currencyRepository.findByNameAndSymbol("TETHER", "USDT").orElseGet(Currency::new);
    pair = pairRepository.findBySymbolIgnoreCase("BTC-USDT").orElseGet(Pair::new);

    exchange = new Exchange();
    exchange.setName("TestEx");
    exchange.setStatus(ExchangeStatus.ACTIVE);
    exchangeRepository.save(exchange);

    account =
        exchangeAccountRepository.save(
            ExchangeAccount.builder()
                .exchange(exchange)
                .label("Testex")
                .apiKey("api-key")
                .secretKey("secret")
                .isPrimary(true)
                .build());

    balanceRepository.save(
        Balance.builder()
            .exchangeAccount(account)
            .currency(quoteCurrency)
            .available(new BigDecimal("1000"))
            .reserved(BigDecimal.ZERO)
            .build());

    stubExchangeMarketClient.reset();
  }

  @Test
  void submitOrder_persistsOrderAndUpdatesBalances() {
    OrderInstructionDto instruction = new OrderInstructionDto();
    instruction.setExchangeName("TestEx");
    instruction.setPairName("BTC-USDT");
    instruction.setSide(OrderSide.BUY);
    DecimalValueDto price = new DecimalValueDto();
    price.setString("10");
    instruction.setPrice(price);
    DecimalValueDto baseAmount = new DecimalValueDto();
    baseAmount.setString("2");
    instruction.setBaseAmount(baseAmount);

    traderService.submitOrder(instruction);

    OrderRequest lastRequest = stubExchangeMarketClient.getLastRequest();
    assertThat(lastRequest).isNotNull();
    assertThat(lastRequest.getSymbol()).isEqualTo("BTC-USDT");
    assertThat(lastRequest.getSide()).isEqualTo(OrderSide.BUY.name());
    assertThat(lastRequest.getQty()).isEqualByComparingTo("2");
    assertThat(lastRequest.getPrice()).isEqualByComparingTo("10");
    assertThat(lastRequest.getTif()).isEqualTo(TimeInForce.IOC.name());

    List<Order> orders = orderRepository.findAll();
    assertThat(orders).hasSize(1);
    Order order = orders.get(0);
    assertThat(order.getExchange()).isEqualTo(exchange);
    assertThat(order.getExchangeAccount()).isEqualTo(account);
    assertThat(order.getPair()).isEqualTo(pair);
    assertThat(order.getSide()).isEqualTo(OrderSide.BUY.name());
    assertThat(order.getType()).isEqualTo("LIMIT");
    assertThat(order.getTif()).isEqualTo(TimeInForce.IOC.name());
    assertThat(order.getPrice()).isEqualByComparingTo("10");
    assertThat(order.getQty()).isEqualByComparingTo("2");
    assertThat(order.getNotional()).isEqualByComparingTo("20");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SENT);
    assertThat(order.getClientOrderId()).isEqualTo("client-123");
    assertThat(order.getExchangeOrderId()).isEqualTo("exchange-456");

    List<BalanceLock> balanceLocks = balanceLockRepository.findAll();
    assertThat(balanceLocks).hasSize(1);
    BalanceLock lock = balanceLocks.get(0);
    assertThat(lock.getExchangeAccount()).isEqualTo(account);
    assertThat(lock.getCurrency()).isEqualTo(quoteCurrency);
    assertThat(lock.getAmount()).isEqualByComparingTo("20");
    assertThat(lock.getReason()).isEqualTo("ORDER_SUBMIT");
    assertThat(lock.getSignalId()).isEqualTo(String.valueOf(order.getId()));

    Balance updatedBalance =
        balanceRepository.findByExchangeAccountAndCurrency(account, quoteCurrency).orElseThrow();
    assertThat(updatedBalance.getAvailable()).isEqualByComparingTo("980");
    assertThat(updatedBalance.getReserved()).isEqualByComparingTo("20");
  }

  @TestConfiguration
  static class TraderServiceIntegrationTestConfig {

    @Bean
    StubExchangeMarketClient stubExchangeMarketClient() {
      return new StubExchangeMarketClient();
    }

    @Bean
    ExchangeClientFactory exchangeMarketClientFactory(
        List<ExchangeMarketClient> exchangeMarketClients) {
      return new ExchangeClientFactory(exchangeMarketClients);
    }
  }

  static class StubExchangeMarketClient implements ExchangeMarketClient {

    private OrderRequest lastRequest;

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
      this.lastRequest = orderRequest;
      return OrderAck.builder()
          .clientOrderId("client-123")
          .exchangeOrderId("exchange-456")
          .status("NEW")
          .build();
    }

    @Override
    public boolean cancelOrder(String orderId) {
      return false;
    }

    @Override
    public ExchangeOrderStatus getOrderStatus(String orderId) {
      return ExchangeOrderStatus.of(OrderStatus.NEW);
    }

    OrderRequest getLastRequest() {
      return lastRequest;
    }

    void reset() {
      this.lastRequest = null;
    }
  }
}
