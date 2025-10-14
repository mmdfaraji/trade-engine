package com.arbitrage.dal;

import com.arbitrage.entities.Order;
import com.arbitrage.respository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;

  public Order save(Order order) {
    return orderRepository.save(order);
  }
}
