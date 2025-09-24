package com.arbitrage.respository;

import com.arbitrage.entities.SignalLeg;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalLegRepository extends JpaRepository<SignalLeg, Long> {}
