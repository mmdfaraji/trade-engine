package com.arbitrage.service;

import com.arbitrage.entities.Signal;
import com.arbitrage.entities.SignalEvent;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.SignalEventType;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.repository.SignalEventRepository;
import com.arbitrage.repository.SignalRepository;
import com.arbitrage.service.api.SignalEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalEventServiceImpl implements SignalEventService {

    private final SignalRepository signalRepository;
    private final SignalEventRepository signalEventRepository;
    private final ObjectMapper mapper;

    @Override
    public void recordReceived(UUID signalId, String externalId, Object payload) {
        try {
            Signal ref = signalRepository.getReferenceById(signalId); // no select, proxy ref
            SignalEvent ev = SignalEvent.builder()
                    .signal(ref)
                    .type(SignalEventType.RECEIVED)
                    .externalId(externalId)
                    .payload(toJson(payload))
                    .build();
            signalEventRepository.save(ev);
            log.info("Event saved: type=RECEIVED, signalId={}, externalId={}", signalId, externalId);
        } catch (Exception e) {
            log.warn("Event save failed (RECEIVED): signalId={}, err={}", signalId, e.getMessage());
        }
    }

    @Override
    public void recordOk(UUID signalId, ValidationPhase phase) {
        try {
            Signal ref = signalRepository.getReferenceById(signalId);
            SignalEvent ev = SignalEvent.builder()
                    .signal(ref)
                    .type(toOkType(phase))
                    .phase(phase)
                    .build();
            signalEventRepository.save(ev);
            log.info("Event saved: type={} (OK), signalId={}", ev.getType(), signalId);
        } catch (Exception e) {
            log.warn("Event save failed (OK): signalId={}, phase={}, err={}", signalId, phase, e.getMessage());
        }
    }

    @Override
    public void recordFailed(UUID signalId, ValidationPhase phase, RejectCode code, String message, Map<String, Object> details) {
        try {
            Signal ref = signalRepository.getReferenceById(signalId);
            SignalEvent ev = SignalEvent.builder()
                    .signal(ref)
                    .type(toFailedType(phase))
                    .phase(phase)
                    .rejectCode(code)
                    .message(message)
                    .payload(toJson(details))
                    .build();
            signalEventRepository.save(ev);
            log.warn("Event saved: type={} (FAILED), signalId={}, code={}, msg={}", ev.getType(), signalId, code, message);
        } catch (Exception e) {
            log.warn("Event save failed (FAILED): signalId={}, phase={}, err={}", signalId, phase, e.getMessage());
        }
    }

    @Override
    public void recordAccepted(UUID signalId) {
        try {
            Signal ref = signalRepository.getReferenceById(signalId);
            SignalEvent ev = SignalEvent.builder()
                    .signal(ref)
                    .type(SignalEventType.ACCEPTED)
                    .build();
            signalEventRepository.save(ev);
            log.info("Event saved: type=ACCEPTED, signalId={}", signalId);
        } catch (Exception e) {
            log.warn("Event save failed (ACCEPTED): signalId={}, err={}", signalId, e.getMessage());
        }
    }

    private String toJson(Object o) {
        try {
            return o == null ? null : mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private SignalEventType toOkType(ValidationPhase phase) {
        return switch (phase) {
            case PHASE0_INTEGRITY -> SignalEventType.INTEGRITY_OK;
            case PHASE0_PERSIST   -> SignalEventType.STATUS_CHANGED;
            case PHASE1_FRESHNESS -> SignalEventType.FRESHNESS_OK;
            case PHASE2_BALANCE   -> SignalEventType.BALANCE_OK;
            case PHASE3_MARKET    -> SignalEventType.MARKET_OK;
            case PHASE4_LIQUIDITY -> SignalEventType.LIQUIDITY_OK;
            case PHASE5_PNL       -> SignalEventType.PNL_OK;
            case PHASE6_RISK      -> SignalEventType.RISK_OK;
        };
    }

    private SignalEventType toFailedType(ValidationPhase phase) {
        return switch (phase) {
            case PHASE0_INTEGRITY -> SignalEventType.INTEGRITY_FAILED;
            case PHASE0_PERSIST   -> SignalEventType.STATUS_CHANGED;
            case PHASE1_FRESHNESS -> SignalEventType.FRESHNESS_FAILED;
            case PHASE2_BALANCE   -> SignalEventType.BALANCE_FAILED;
            case PHASE3_MARKET    -> SignalEventType.MARKET_FAILED;
            case PHASE4_LIQUIDITY -> SignalEventType.LIQUIDITY_FAILED;
            case PHASE5_PNL       -> SignalEventType.PNL_FAILED;
            case PHASE6_RISK      -> SignalEventType.RISK_FAILED;
        };
    }
}
