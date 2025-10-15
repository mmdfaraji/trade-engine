package com.arbitrage.dto.processor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;

/**
 * Output of the persist step. If 'stop' is present, the pipeline should terminate with that
 * ProcessResult. Otherwise 'id' is the saved signal id to continue the pipeline.
 */
public final class PersistSignalResult {
  @Getter private final UUID id;
  private final ProcessResult stop;

  private PersistSignalResult(UUID id, ProcessResult stop) {
    this.id = id;
    this.stop = stop;
  }

  public static PersistSignalResult continued(UUID id) {
    Objects.requireNonNull(id, "id");
    return new PersistSignalResult(id, null);
  }

  public static PersistSignalResult terminated(ProcessResult stop) {
    Objects.requireNonNull(stop, "stop");
    return new PersistSignalResult(null, stop);
  }

  public Optional<ProcessResult> getStop() {
    return Optional.ofNullable(stop);
  }
}
