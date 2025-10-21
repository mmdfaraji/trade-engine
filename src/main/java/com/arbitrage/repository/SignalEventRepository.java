package com.arbitrage.repository;

import com.arbitrage.entities.SignalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalEventRepository extends JpaRepository<SignalEvent, Long> {}
