package com.arbitrage.dto.signal;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MetaDto {
  private Boolean clockwise;
  private String couple;
  private String pair;

  private String signalId; // required in new payload
  private Instant createdAt; // from payload meta or backfilled via NATS metadata

  private Long ttlMs; // required for freshness policy
  private Long maxLatencyMs; // optional
}
