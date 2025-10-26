package com.arbitrage.dto.balance;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SizingResultDto {
    private final boolean scaled;          // true if alpha < 1
    private final BigDecimal scaleRatio;   // alpha in [0,1]
    private final List<BigDecimal> execQty; // per-leg execution qty (same ordering as input)
}