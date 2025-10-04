package com.arbitrage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConstraintsDto {

  @JsonProperty("Max_slippage_bps")
  private Integer maxSlippageBps;

  @JsonProperty("Min_expected_pnl")
  private Long minExpectedPnl;

  @JsonProperty("Max_latency_ms")
  private Long maxLatencyMs;
}
