package com.arbitrage.repository;

import com.arbitrage.entities.Balance;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BalanceRepository extends JpaRepository<Balance, Long> {
  // Atomic conditional update: reserve if and only if available >= amount
  @Modifying
  @Query(
      "UPDATE Balance b "
          + "SET b.available = b.available - :amount, b.reserved = b.reserved + :amount "
          + "WHERE b.exchangeAccount.id = :accountId AND b.currency.id = :currencyId "
          + "AND b.available >= :amount")
  int tryReserve(Long accountId, Long currencyId, BigDecimal amount);
}
