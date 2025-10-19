package com.arbitrage.dal;

import com.arbitrage.entities.Order;
import com.arbitrage.enums.OrderStatus;
import com.arbitrage.respository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;

  public Order save(Order order) {
    return orderRepository.save(order);
  }

  public List<Order> findByStatus(OrderStatus status) {
    return orderRepository.findByStatus(status);
  }
}
