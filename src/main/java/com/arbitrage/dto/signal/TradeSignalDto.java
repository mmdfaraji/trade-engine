package com.arbitrage.dto.signal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Root DTO for inbound signal, aligned 1:1 with the NATS JSON. Additional fields ttlMs and
 * maxLatencyMs are expected to be added to the JSON. 'createdAt' can be filled from NATS metadata
 * by the consumer if not present in payload.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeSignalDto {

  // JSON field name is "class"; map it to 'clazz' to avoid keyword confusion.
  @JsonProperty("class")
  private String clazz;

  private MetaDto meta;

  // Expected to be included in JSON soon
  private Long ttlMs; // required for freshness policy
  private Long maxLatencyMs; // optional

  // May be set by consumer from NATS metadata timestamp if not present
  private Instant createdAt;

  private List<OrderInstructionDto> orders;
}
