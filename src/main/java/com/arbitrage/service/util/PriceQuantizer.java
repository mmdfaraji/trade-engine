package com.arbitrage.service.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class PriceQuantizer {

  private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

  private PriceQuantizer() {}

  public static BigDecimal quantizeDown(BigDecimal price, BigDecimal tick) {
    if (price == null) return null;
    if (tick == null || tick.signum() <= 0) return price;
    if (price.signum() <= 0) return price;
    BigDecimal steps = price.divide(tick, 0, RoundingMode.DOWN);
    return steps.multiply(tick, MC);
  }

  public static BigDecimal quantizeUp(BigDecimal price, BigDecimal tick) {
    if (price == null) return null;
    if (tick == null || tick.signum() <= 0) return price;
    if (price.signum() <= 0) return price;
    BigDecimal steps = price.divide(tick, 0, RoundingMode.CEILING);
    return steps.multiply(tick, MC);
  }
}
