package com.arbitrage.dto.processor;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable step outcome for a single validation step. - ok=true => step passed - ok=false => step
 * failed and a Rejection is provided Keep this class minimal and let Rejection carry details.
 */
@Getter
@Builder
@AllArgsConstructor
public class StepResult {

  // true => validation passed; false => failed
  private final boolean ok;

  // present only when ok=false
  private final Optional<Rejection> rejection;

  /** Convenience: create a passing result (no rejection). */
  public static StepResult pass() {
    return StepResult.builder().ok(true).rejection(Optional.empty()).build();
  }

  /** Convenience: create a failing result with a rejection payload. */
  public static StepResult fail(Rejection rejection) {
    return StepResult.builder().ok(false).rejection(Optional.ofNullable(rejection)).build();
  }
}
