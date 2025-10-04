package com.arbitrage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalMessageDto {

  @JsonProperty("signal_id")
  private String signalId;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("ttl_ms")
  private Long ttlMs;

  private List<SignalLegDto> legs;
  private ConstraintsDto constraints;
  private String source;
  private BigDecimal expectedPnl;
}
