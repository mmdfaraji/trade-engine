package com.arbitrage.respository;

import com.arbitrage.entities.Order;
import com.arbitrage.enums.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByStatus(OrderStatus status);
}
