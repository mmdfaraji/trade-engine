package com.arbitrage.service.workflow;

import com.arbitrage.dto.processor.ProcessResult;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.enums.SignalStatus;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.SignalEventService;
import com.arbitrage.service.api.SignalService;
import com.arbitrage.service.workflow.steps.DecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionServiceImpl implements DecisionService {

    private final SignalService signalService;
    private final SignalEventService signalEventService;

    @Override
    public Optional<ProcessResult> handle(SignalContext ctx, StepResult stepResult, ValidationPhase phase) {
        if (stepResult.isOk()) {
            return Optional.empty();
        }

        try {
            signalService.updateStatus(ctx.getSavedSignalId(), SignalStatus.REJECTED);
        } catch (Exception e) {
            log.warn("Status update to REJECTED failed: signalId={}", ctx.getSavedSignalId(), e);
        }

        Optional<Rejection> rejOpt = stepResult.getRejection();
        Rejection rej = rejOpt.orElse(null);
        String code = rej != null ? String.valueOf(rej.getCode()) : "n/a";
        String msg = rej != null ? rej.getMessage() : "n/a";

        // audit event for FAILED step
        if (rej != null) {
            try {
                signalEventService.recordFailed(
                        ctx.getSavedSignalId(),
                        phase,
                        rej.getCode(),
                        rej.getMessage(),
                        rej.getDetails());
            } catch (Exception e) {
                log.warn("Event recording ({}_FAILED) failed: signalId={}, err={}", stepTag(phase), ctx.getSavedSignalId(), e.getMessage(), e);
            }
        }

        log.warn("Rejected @ {}: signalId={}, code={}, msg={}", stepTag(phase), ctx.getSavedSignalId(), code, msg);
        List<Rejection> reasons = rejOpt.map(List::of).orElse(List.of());
        return Optional.of(ProcessResult.rejected(ctx.getSavedSignalId(), reasons));
    }

    private String stepTag(ValidationPhase phase) {
        return switch (phase) {
            case PHASE0_INTEGRITY -> "integrity";
            case PHASE0_PERSIST -> "persist";
            case PHASE1_FRESHNESS -> "freshness";
            case PHASE2_BALANCE -> "balance";
            case PHASE3_MARKET -> "market";
            case PHASE4_LIQUIDITY -> "liquidity";
            case PHASE5_PNL -> "pnl";
            case PHASE6_RISK -> "risk";
        };
    }
}
