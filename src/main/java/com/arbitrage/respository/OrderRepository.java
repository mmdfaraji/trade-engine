package com.arbitrage.respository;

import com.arbitrage.entities.Order;
import com.arbitrage.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  java.util.List<Order> findByStatus(OrderStatus status);
}
