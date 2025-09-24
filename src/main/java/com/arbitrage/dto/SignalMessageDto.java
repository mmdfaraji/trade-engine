package com.arbitrage.dto;

import com.arbitrage.enums.SignalStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalMessageDto {
  private Long ttlMs;
  private SignalStatus status;
  private String source;
  private String constraints; // raw JSON or text
  private BigDecimal expectedPnl;
  private List<SignalLegDto> legs;
}
