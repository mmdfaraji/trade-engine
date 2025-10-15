package com.arbitrage.dto.processor;

import java.util.Optional;
import lombok.Getter;

@Getter
public final class StepResult {

  private final boolean ok;
  private final Rejection rejection; // null if ok==true

  private StepResult(boolean ok, Rejection rejection) {
    this.ok = ok;
    this.rejection = rejection;
  }

  public static StepResult ok() {
    return new StepResult(true, null);
  }

  public static StepResult fail(Rejection rejection) {
    if (rejection == null) throw new IllegalArgumentException("rejection is null");
    return new StepResult(false, rejection);
  }

  public Optional<Rejection> getRejection() {
    return Optional.ofNullable(rejection);
  }
}
