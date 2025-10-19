package com.arbitrage.service;

import com.arbitrage.dal.OrderService;
import com.arbitrage.entities.Balance;
import com.arbitrage.entities.BalanceLock;
import com.arbitrage.entities.Currency;
import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.entities.Order;
import com.arbitrage.entities.Pair;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.model.ExchangeOrderStatus;
import com.arbitrage.respository.BalanceLockRepository;
import com.arbitrage.respository.BalanceRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

  private static final String DEFAULT_DELAY = "30000"; // 30 seconds

  private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final String BALANCE_LOCK_REASON = "ORDER_SUBMIT";

  private final OrderService orderService;
  private final ExchangeClientFactory exchangeClientFactory;
  private final BalanceLockRepository balanceLockRepository;
  private final BalanceRepository balanceRepository;

  @Value("${app.order-status.timeout:PT5M}")
  private Duration orderTimeout;

  @Scheduled(fixedDelayString = "${app.order-status.poll-delay:" + DEFAULT_DELAY + "}")
  @Transactional
  public void refreshSentOrdersStatus() {
    List<Order> sentOrders = orderService.findByStatus(OrderStatus.SENT);
    if (sentOrders == null || sentOrders.isEmpty()) {
      return;
    }

    for (Order order : sentOrders) {
      if (order == null) {
        continue;
      }
      try {
        Exchange exchange = order.getExchange();
        if (exchange == null || !StringUtils.hasText(exchange.getName())) {
          log.debug("Skipping order {} due to missing exchange", order.getId());
          continue;
        }

        String exchangeOrderId = order.getExchangeOrderId();
        if (!StringUtils.hasText(exchangeOrderId)) {
          exchangeOrderId = order.getClientOrderId();
        }
        if (!StringUtils.hasText(exchangeOrderId)) {
          log.debug("Skipping order {} due to missing identifiers", order.getId());
          continue;
        }

        ExchangeMarketClient client = exchangeClientFactory.getClient(exchange.getName());
        ExchangeOrderStatus statusDetails = client.getOrderStatus(exchangeOrderId);
        if (statusDetails == null) {
          continue;
        }

        OrderStatus fetchedStatus = statusDetails.status();
        BigDecimal executedQty = statusDetails.filledQuantity();
        BigDecimal avgPrice = statusDetails.averagePrice();
        BigDecimal executedNotional = statusDetails.executedNotional();
        if (executedNotional == null
            && executedQty != null
            && executedQty.signum() > 0
            && order.getPrice() != null) {
          BigDecimal referencePrice =
              avgPrice != null && avgPrice.signum() > 0 ? avgPrice : order.getPrice();
          executedNotional = referencePrice.multiply(executedQty, MATH_CONTEXT);
        }

        boolean updated = false;

        if (avgPrice != null && differs(order.getAvgPrice(), avgPrice)) {
          order.setAvgPrice(avgPrice);
          updated = true;
        }
        if (executedQty != null && differs(order.getQtyExec(), executedQty)) {
          order.setQtyExec(executedQty);
          order.setFilledQty(executedQty);
          updated = true;
        }

        BigDecimal orderQty = order.getQty();
        if (orderQty != null
            && executedQty != null
            && orderQty.signum() > 0
            && executedQty.compareTo(orderQty) >= 0) {
          fetchedStatus = OrderStatus.FILLED;
        } else if (executedQty != null
            && executedQty.signum() > 0
            && fetchedStatus != OrderStatus.CANCELLED
            && fetchedStatus != OrderStatus.FILLED) {
          fetchedStatus = OrderStatus.PARTIAL;
        }

        boolean timedOut = hasTimedOut(order);
        if (timedOut
            && fetchedStatus != OrderStatus.FILLED
            && fetchedStatus != OrderStatus.CANCELLED) {
          boolean cancelled = client.cancelOrder(exchangeOrderId);
          if (cancelled) {
            fetchedStatus = OrderStatus.CANCELLED;
          }
        }

        if (fetchedStatus != null && fetchedStatus != order.getStatus()) {
          log.info(
              "Order {} status changed from {} to {} by {}",
              order.getId(),
              order.getStatus(),
              fetchedStatus,
              exchange.getName());
          order.setStatus(fetchedStatus);
          updated = true;
        }

        if ((fetchedStatus == OrderStatus.FILLED || fetchedStatus == OrderStatus.CANCELLED)
            && order.getClosedAt() == null) {
          order.setClosedAt(new Date());
          updated = true;
        }

        if (executedQty != null
            || executedNotional != null
            || fetchedStatus == OrderStatus.FILLED
            || fetchedStatus == OrderStatus.CANCELLED
            || fetchedStatus == OrderStatus.PARTIAL) {
          updated |= updateBalances(order, executedQty, executedNotional, fetchedStatus);
        }

        if (updated) {
          orderService.save(order);
        }
      } catch (Exception ex) {
        log.warn("Failed to refresh status for order {}: {}", order.getId(), ex.getMessage());
        log.debug("Order status refresh error", ex);
      }
    }
  }

  private boolean updateBalances(
      Order order, BigDecimal executedQty, BigDecimal executedNotional, OrderStatus newStatus) {
    ExchangeAccount account = order.getExchangeAccount();
    Pair pair = order.getPair();
    if (account == null || pair == null) {
      return false;
    }

    boolean buySide = "BUY".equalsIgnoreCase(order.getSide());
    Currency currency = buySide ? pair.getQuoteCurrency() : pair.getBaseCurrency();
    if (currency == null) {
      return false;
    }

    BalanceLock lock = findBalanceLock(order, account, currency);
    Balance balance =
        balanceRepository.findByExchangeAccountAndCurrency(account, currency).orElse(null);

    if (lock == null && balance == null) {
      return false;
    }

    BigDecimal lockOriginal = buySide ? order.getNotional() : order.getQty();
    if (lockOriginal == null) {
      lockOriginal = ZERO;
    }

    BigDecimal executedForLock = buySide ? executedNotional : executedQty;
    if (executedForLock == null || executedForLock.signum() < 0) {
      executedForLock = ZERO;
    }
    if (lockOriginal.signum() > 0 && executedForLock.compareTo(lockOriginal) > 0) {
      executedForLock = lockOriginal;
    }

    BigDecimal remaining = lockOriginal.subtract(executedForLock, MATH_CONTEXT);
    if (remaining.signum() < 0) {
      remaining = ZERO;
    }

    BigDecimal targetLockAmount = remaining;
    if (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.FILLED) {
      targetLockAmount = ZERO;
    }

    BigDecimal previousLockAmount = lock != null ? zeroIfNull(lock.getAmount()) : ZERO;
    boolean changed = false;

    if (lock != null && differs(previousLockAmount, targetLockAmount)) {
      lock.setAmount(targetLockAmount);
      balanceLockRepository.save(lock);
      changed = true;
    }

    if (balance != null) {
      BigDecimal reserved = zeroIfNull(balance.getReserved());
      BigDecimal available = zeroIfNull(balance.getAvailable());

      BigDecimal newReserved =
          reserved.subtract(previousLockAmount, MATH_CONTEXT).add(targetLockAmount, MATH_CONTEXT);
      if (newReserved.signum() < 0) {
        newReserved = ZERO;
      }
      balance.setReserved(newReserved);

      if (newStatus == OrderStatus.CANCELLED) {
        BigDecimal release = previousLockAmount.subtract(targetLockAmount, MATH_CONTEXT);
        if (release.signum() > 0) {
          balance.setAvailable(available.add(release, MATH_CONTEXT));
        }
      }

      balanceRepository.save(balance);
      changed = true;
    }

    return changed;
  }

  private BalanceLock findBalanceLock(Order order, ExchangeAccount account, Currency currency) {
    if (order.getId() == null) {
      return null;
    }
    return balanceLockRepository
        .findByExchangeAccountAndCurrencyAndReasonAndSignalId(
            account, currency, BALANCE_LOCK_REASON, String.valueOf(order.getId()))
        .orElse(null);
  }

  private boolean hasTimedOut(Order order) {
    if (orderTimeout == null || orderTimeout.isZero() || orderTimeout.isNegative()) {
      return false;
    }
    Date sentAt = order.getSentAt();
    if (sentAt == null) {
      return false;
    }
    Instant expiry = sentAt.toInstant().plus(orderTimeout);
    return Instant.now().isAfter(expiry);
  }

  private static boolean differs(BigDecimal current, BigDecimal next) {
    if (current == null && next == null) {
      return false;
    }
    if (current == null || next == null) {
      return true;
    }
    return current.compareTo(next) != 0;
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value != null ? value : ZERO;
  }
}
