package com.arbitrage.service.workflow;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalService;
import com.arbitrage.service.workflow.steps.DecisionService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionServiceImpl implements DecisionService {

  private final SignalService signalService;

  @Override
  public Optional<ProcessResult> handle(
      SignalContext ctx, StepResult stepResult, ValidationPhase phase) {
    if (stepResult.isOk()) {
      return Optional.empty();
    }
    try {
      signalService.updateStatus(ctx.getSavedSignalId(), SignalStatus.REJECTED);
    } catch (Exception e) {
      log.warn("Status update to REJECTED failed: signalId={}", ctx.getSavedSignalId(), e);
    }
    List<Rejection> reasons = stepResult.getRejection().map(List::of).orElse(List.of());
    log.warn(
        "Rejected at {}: signalId={}, code={}, msg={}",
        phase,
        ctx.getSavedSignalId(),
        reasons.isEmpty() ? "n/a" : reasons.get(0).getCode(),
        reasons.isEmpty() ? "n/a" : reasons.get(0).getMessage());
    return Optional.of(ProcessResult.rejected(ctx.getSavedSignalId(), reasons));
  }
}
