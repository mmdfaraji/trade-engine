package com.arbitrage.dto.signal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Meta section of inbound JSON. Example: { "clockwise": "false", "couple": "nobitex-ramzinex",
 * "pair": "ETH-USDT" }
 *
 * <p>Jackson will coerce "true"/"false" strings to Boolean if configured; otherwise they still
 * deserialize into Boolean via standard coercion. If not, change type to String.
 */
@Getter
@Setter
@NoArgsConstructor
public class MetaDto {
  private Boolean clockwise; // may come as "true"/"false" string; Jackson usually coerces
  private String couple;
  private String pair;
}
