package com.arbitrage.service.validators.market;

import com.arbitrage.dto.market.MarketPairKeyDto;
import com.arbitrage.dto.market.MarketValidationReportDto;
import com.arbitrage.dto.plan.ExecutionLegPlanDto;
import com.arbitrage.dto.plan.ExecutionPlanDto;
import com.arbitrage.dto.processor.Rejection;
import com.arbitrage.dto.processor.SignalContext;
import com.arbitrage.dto.processor.StepResult;
import com.arbitrage.entities.PairExchange;
import com.arbitrage.enums.OrderSide;
import com.arbitrage.enums.RejectCode;
import com.arbitrage.enums.ValidationPhase;
import com.arbitrage.service.api.PairExchangeService;
import com.arbitrage.service.util.PriceQuantizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRulesServiceImpl implements MarketRulesService {

  private final PairExchangeService pairExchangeService;
  private final Clock clock;

  @Override
  public MarketValidationReportDto applyAll(SignalContext ctx, ExecutionPlanDto plan) {
    // For now only price guards / decimal alignment
    return applyPriceGuards(ctx, plan);
  }

  @Override
  public MarketValidationReportDto applyPriceGuards(SignalContext ctx, ExecutionPlanDto plan) {
    String sigId = (ctx.getDto().getMeta() != null) ? ctx.getDto().getMeta().getSignalId() : "n/a";

    try {
      Map<MarketPairKeyDto, PairExchange> rules = pairExchangeService.findRulesForPlan(plan);

      List<BigDecimal> priceBefore = new ArrayList<>();
      List<BigDecimal> priceAfter = new ArrayList<>();
      List<BigDecimal> tickApplied = new ArrayList<>();

      ExecutionPlanDto.ExecutionPlanDtoBuilder pb = ExecutionPlanDto.builder();

      for (ExecutionLegPlanDto leg : plan.getLegs()) {
        PairExchange pe = rules.get(new MarketPairKeyDto(leg.getExchangeId(), leg.getPairId()));

        BigDecimal inputPrice = leg.getPrice();
        priceBefore.add(inputPrice);

        BigDecimal tick = (pe != null ? pe.getTickSize() : null);

        BigDecimal guarded;
        if (leg.getSide() == OrderSide.BUY) {
          guarded = PriceQuantizer.quantizeUp(inputPrice, tick);
        } else {
          guarded = PriceQuantizer.quantizeDown(inputPrice, tick);
        }

        if (guarded == null || guarded.signum() <= 0) {
          Rejection rej =
              Rejection.builder()
                  .code(RejectCode.MARKET_RULE_VIOLATION)
                  .message("Invalid price after quantization")
                  .phase(ValidationPhase.PHASE3_MARKET)
                  .validator("MarketRulesService")
                  .occurredAt(clock.instant())
                  .detail("exchangeId", leg.getExchangeId())
                  .detail("pairId", leg.getPairId())
                  .detail("inputPrice", inputPrice)
                  .detail("tick", tick)
                  .build();
          log.warn(
              "market price invalid: signalId={}, ex={}, pair={}, price={}, tick={}",
              sigId,
              leg.getExchangeId(),
              leg.getPairId(),
              inputPrice,
              tick);
          return MarketValidationReportDto.builder().result(StepResult.fail(rej)).build();
        }

        priceAfter.add(guarded);
        tickApplied.add(tick);

        ExecutionLegPlanDto adjusted =
            ExecutionLegPlanDto.builder()
                .index(leg.getIndex())
                .exchangeId(leg.getExchangeId())
                .exchangeAccountId(leg.getExchangeAccountId())
                .pairId(leg.getPairId())
                .baseCurrencyId(leg.getBaseCurrencyId())
                .quoteCurrencyId(leg.getQuoteCurrencyId())
                .spendCurrencyId(leg.getSpendCurrencyId())
                .receiveCurrencyId(leg.getReceiveCurrencyId())
                .exchangeName(leg.getExchangeName())
                .marketSymbol(leg.getMarketSymbol())
                .side(leg.getSide())
                .reqQty(leg.getReqQty())
                .price(guarded) // adjusted price
                .requiredSpend(leg.getRequiredSpend())
                .execQty(leg.getExecQty()) // unchanged here
                .build();

        pb.leg(adjusted);
      }

      ExecutionPlanDto adjustedPlan = pb.build();

      log.info("price guard ok: signalId={}, legs={}", sigId, plan.getLegs().size());

      return MarketValidationReportDto.builder()
          .result(StepResult.ok())
          .plan(adjustedPlan)
          .priceBefore(priceBefore)
          .priceAfter(priceAfter)
          .tickApplied(tickApplied)
          .build();

    } catch (Exception e) {
      Rejection rej =
          Rejection.builder()
              .code(RejectCode.TRANSIENT_UPSTREAM)
              .message("Market price guard failed")
              .phase(ValidationPhase.PHASE3_MARKET)
              .validator("MarketRulesService")
              .occurredAt(clock.instant())
              .detail("error", e.getMessage())
              .build();
      log.error(
          "market price guard failed: signalId={}, err={}",
          (ctx.getSavedSignalId() != null ? ctx.getSavedSignalId() : "n/a"),
          e.getMessage(),
          e);

      return MarketValidationReportDto.builder().result(StepResult.fail(rej)).build();
    }
  }
}
