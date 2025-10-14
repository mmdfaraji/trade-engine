package com.arbitrage.respository;

import com.arbitrage.entities.BalanceLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceLockRepository extends JpaRepository<BalanceLock, Long> {}
