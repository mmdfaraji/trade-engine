package com.arbitrage.service.balance;

import com.arbitrage.dto.plan.BalanceReservationResultDto;
import com.arbitrage.dto.processor.SignalContext;

public interface BalanceReservationService {
  /**
   * All-or-nothing balance check & reservation in a single transaction. On success: reserves funds
   * and returns plan (execQty=reqQty). On failure: nothing is reserved and a rejection is returned.
   */
  BalanceReservationResultDto reserveForSignal(SignalContext ctx);
}
