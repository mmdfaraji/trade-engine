package com.arbitrage.dto.signal;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for numeric fields in the inbound JSON (price/baseAmount/quoteAmount). It accepts {string,
 * float64, float64Exact, ratStr}. Use toBigDecimal() when needed.
 */
@Getter
@Setter
@NoArgsConstructor
public class DecimalValueDto {
  private String string;
  private Double float64;
  private Boolean float64Exact;
  private String ratStr;

  public BigDecimal toBigDecimal() {
    // Prefer exact string; fallback to float64; otherwise zero
    if (string != null && !string.isEmpty()) {
      return new BigDecimal(string);
    }
    if (float64 != null) {
      return BigDecimal.valueOf(float64);
    }
    return BigDecimal.ZERO;
  }
}
