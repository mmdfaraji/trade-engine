package com.arbitrage.service;

import com.arbitrage.dal.OrderService;
import com.arbitrage.dto.OrderInstructionDto;
import com.arbitrage.entities.Balance;
import com.arbitrage.entities.BalanceLock;
import com.arbitrage.entities.Currency;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.entities.Order;
import com.arbitrage.entities.Pair;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.enums.TimeInForce;
import com.arbitrage.model.OrderAck;
import com.arbitrage.model.OrderRequest;
import com.arbitrage.respository.BalanceLockRepository;
import com.arbitrage.respository.BalanceRepository;
import com.arbitrage.respository.PairRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Service
public class TraderService implements Trader {

  private static final Locale LOCALE = Locale.ROOT;
  private static final MathContext DEFAULT_MATH_CONTEXT = MathContext.DECIMAL64;
  private static final String DEFAULT_ORDER_TYPE = "LIMIT";
  private static final String BALANCE_LOCK_REASON = "ORDER_SUBMIT";

  private final ExchangeClientFactory exchangeClientFactory;
  private final ExchangeAccessService exchangeAccessService;
  private final PairRepository pairRepository;
  private final OrderService orderService;
  private final BalanceLockRepository balanceLockRepository;
  private final BalanceRepository balanceRepository;

  @Transactional
  public void submitOrder(OrderInstructionDto instruction) {
    Objects.requireNonNull(instruction, "orderInstructionDto must not be null");

    String exchangeName = requireText(instruction.getExchangeName(), "exchangeName");
    ExchangeMarketClient client = exchangeClientFactory.getClient(exchangeName);

    String pairSymbol = normalizePairSymbol(requireText(instruction.getPairName(), "pairName"));
    Pair pair =
        pairRepository
            .findBySymbolIgnoreCase(pairSymbol)
            .orElseThrow(() -> new IllegalArgumentException("Unknown pair: " + pairSymbol));

    OrderSide side = Optional.ofNullable(instruction.getSide()).orElse(OrderSide.BUY);
    BigDecimal price = requirePositive(instruction.getPriceAsBigDecimal(), "price");
    BigDecimal baseQty = resolveBaseQuantity(instruction, price);
    BigDecimal notional = price.multiply(baseQty, DEFAULT_MATH_CONTEXT);
    BigDecimal quoteQty = resolveQuoteAmount(instruction, notional);

    TimeInForce tif = instruction.getTimeInForceOrDefault(TimeInForce.IOC);

    OrderRequest request =
        OrderRequest.builder()
            .symbol(pairSymbol)
            .side(side.name())
            .qty(baseQty)
            .price(price)
            .tif(tif.name())
            .build();

    OrderAck ack = client.submitOrder(request);

    Exchange exchange = exchangeAccessService.requireExchange(exchangeName);
    ExchangeAccount account =
        exchangeAccessService.requireAccount(exchangeName, defaultAccountLabel(exchangeName));

    Order order =
        Order.builder()
            .exchange(exchange)
            .exchangeAccount(account)
            .pair(pair)
            .side(side.name())
            .type(DEFAULT_ORDER_TYPE)
            .tif(tif.name())
            .clientOrderId(ack != null ? ack.getClientOrderId() : null)
            .exchangeOrderId(ack != null ? ack.getExchangeOrderId() : null)
            .price(price)
            .qty(baseQty)
            .qtyExec(BigDecimal.ZERO)
            .notional(notional)
            .status(OrderStatus.SENT)
            .filledQty(BigDecimal.ZERO)
            .avgPrice(BigDecimal.ZERO)
            .sentAt(new Date())
            .build();
    orderService.save(order);

    applyBalanceLock(account, pair, side, baseQty, quoteQty);
  }

  private void applyBalanceLock(
      ExchangeAccount account, Pair pair, OrderSide side, BigDecimal baseQty, BigDecimal quoteQty) {

    BigDecimal lockAmount;
    Currency currency;

    // ToDo: send orderRequest to exchange from orderInstructionDto object
    if (side == OrderSide.BUY) {
      currency = pair.getQuoteCurrency();
      lockAmount = quoteQty;
    } else {
      currency = pair.getBaseCurrency();
      lockAmount = baseQty;
    }

    // ToDo: save order object in database
    lockAmount = requirePositive(lockAmount, "lockAmount");

    BalanceLock lock =
        BalanceLock.builder()
            .exchangeAccount(account)
            .currency(currency)
            .amount(lockAmount)
            .reason(BALANCE_LOCK_REASON)
            .build();
    balanceLockRepository.save(lock);

    Balance balance =
        balanceRepository
            .findByExchangeAccountAndCurrency(account, currency)
            .orElseGet(
                () ->
                    Balance.builder()
                        .exchangeAccount(account)
                        .currency(currency)
                        .available(BigDecimal.ZERO)
                        .reserved(BigDecimal.ZERO)
                        .build());

    BigDecimal available =
        defaultZero(balance.getAvailable()).subtract(lockAmount, DEFAULT_MATH_CONTEXT);
    BigDecimal reserved = defaultZero(balance.getReserved()).add(lockAmount, DEFAULT_MATH_CONTEXT);
    balance.setAvailable(available);
    balance.setReserved(reserved);
    balanceRepository.save(balance);
  }

  private BigDecimal resolveBaseQuantity(OrderInstructionDto instruction, BigDecimal price) {
    BigDecimal base = instruction.getBaseAmountAsBigDecimal();
    if (base != null && base.signum() > 0) {
      return base;
    }

    BigDecimal quote = instruction.getQuoteAmountAsBigDecimal();
    if (quote != null && quote.signum() > 0) {
      return quote.divide(price, DEFAULT_MATH_CONTEXT);
    }

    throw new IllegalArgumentException("Order quantity must be positive");
  }

  private BigDecimal resolveQuoteAmount(OrderInstructionDto instruction, BigDecimal defaultValue) {
    BigDecimal quote = instruction.getQuoteAmountAsBigDecimal();
    return (quote != null && quote.signum() > 0) ? quote : defaultValue;
  }

  private BigDecimal requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  private String requireText(String value, String field) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private String normalizePairSymbol(String symbol) {
    return symbol.trim().replace('_', '-').toUpperCase(LOCALE);
  }

  private BigDecimal defaultZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  // ToDo: update BalanceLock table and Balance table
  private String defaultAccountLabel(String exchangeName) {
    String normalized = exchangeName.trim().toLowerCase(LOCALE);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("exchangeName must not be blank");
    }
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }
}
