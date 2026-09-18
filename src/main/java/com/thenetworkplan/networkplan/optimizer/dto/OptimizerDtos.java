package com.thenetworkplan.networkplan.optimizer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que le Timeline Optimizer lit et rend — les objets de {@code optLive},
 * {@code Benchmark.run()} et {@code deriveOps()} de l'annexe, sans le tirage
 * aleatoire.
 */
public final class OptimizerDtos {

    private OptimizerDtos() {
    }

    /** Les neuf couts globaux de {@code COST_GLOBALS} (prototype l. 90668). */
    public record CostParamsDto(
            double fuelPrice,
            double delayPerMin,
            double cxlPenalty,
            double aogPerDay,
            double crewDisrupt,
            double crewDeadhead,
            double nightStop,
            double charterYield,
            double mxSlotMiss,
            String currency,
            String symbol,
            String baseApt) implements Serializable {
    }

    /** Une ligne de {@code COST_TYPES} : les taux horaires d'un type. */
    public record TypeRateDto(String key, String label, double fuelGalPerHour, double mxPerFh,
                              double enginePerFh, double crewPerFh, double handlingPerCycle,
                              double navPerNm) implements Serializable {
    }

    /** Une famille de problemes que le moteur a le droit de toucher — {@code OPT_SCOPE}. */
    public record ScopeDto(String key, String label, List<String> types) implements Serializable {
    }

    /** Un module lu par l'optimiseur — {@code LiveAdapter.sources()}. */
    public record SourceDto(String key, String label, boolean ok, String detail) implements Serializable {
    }

    /** Tout ce que la fenetre affiche avant qu'on lance l'analyse. */
    public record OptimizerSetupDto(
            List<SourceDto> sources,
            List<ScopeDto> scopes,
            CostParamsDto defaultCosts,
            List<TypeRateDto> typeRates,
            LocalDate today) implements Serializable {
    }

    public record OptimizeCommand(
            LocalDate startDate,
            @Min(1) @Max(14) Integer days,
            Double fromHour,
            Double toHour,
            List<String> scope,
            String objective,
            Double target,
            CostParamsDto costs) {
    }

    /** Une inefficience detectee — {@code Detector.mk()} (l. 93205). */
    public record OptAnomalyDto(
            String id,
            String type,
            String label,
            String severity,
            String expectedFix,
            String scopeKey,
            boolean inScope,
            String registration,
            List<String> flightNos,
            List<UUID> legIds,
            String note,
            int estimatedValue) implements Serializable {
    }

    /** Une action du moteur — {@code act()} (l. 91016), avec son gain physique et sa valeur. */
    public record OptActionDto(
            String anomalyId,
            String type,
            String label,
            String action,
            String severity,
            List<String> flights,
            List<String> aircraft,
            List<String> crew,
            Map<String, Object> gain,
            boolean advisory,
            int valueUsd,
            String description,
            String step) implements Serializable {
    }

    /**
     * Une instruction ops atomique — {@code deriveOps()} (l. 93592).
     *
     * <p>{@code applicable} dit si le bouton « Apply » existe : vrai quand une
     * route du serveur execute exactement ce geste (changer d'appareil,
     * re-horodater, annuler un ferry), faux quand l'instruction se passe dans
     * un autre module (re-routage, creation d'une etape, equipage).
     */
    public record OptOperationDto(
            String id,
            String op,
            String date,
            String flightNo,
            String registration,
            UUID legId,
            String fromReg,
            String toReg,
            OffsetDateTime newStd,
            OffsetDateTime newSta,
            OffsetDateTime oldStd,
            OffsetDateTime oldSta,
            boolean applicable,
            boolean ferry,
            String title,
            String brief,
            String text,
            int valueUsd,
            String anomalyId) implements Serializable {
    }

    /** Un suivi a faire dans un autre module — {@code deriveFollowUps()} (l. 93823). */
    public record OptFollowUpDto(String id, String module, String brief, String text) implements Serializable {
    }

    /** Le cout du plan, poste par poste — {@code Cost.planCost()} (l. 90719). */
    public record PlanCostDto(long flying, long handling, long nav, long night, long delays, long cancellations,
                              long aog, long mxExposure, long lostRevenue, long crewDisruption,
                              long total) implements Serializable {
    }

    /** Le resultat entier — {@code rec} de {@code Benchmark.run()} (l. 91414) plus les ops. */
    public record OptimizationResultDto(
            String scenarioId,
            String scenarioName,
            LocalDate startDate,
            int days,
            double fromHour,
            double toHour,
            int flights,
            int tails,
            String objective,
            double target,
            List<String> scope,
            String rankedBy,
            boolean stoppedEarly,
            int detected,
            int corrected,
            int skipped,
            double initialScore,
            double finalScore,
            double improvementPct,
            long costBefore,
            long costAfter,
            long costSaving,
            double costSavingPct,
            long costAnnual,
            PlanCostDto costBreakdownBefore,
            PlanCostDto costBreakdownAfter,
            long computeMs,
            String currency,
            String symbol,
            int exclusiveUseKept,
            List<String> exclusiveUseDetail,
            List<OptAnomalyDto> anomalies,
            List<OptActionDto> actions,
            List<OptOperationDto> operations,
            List<OptFollowUpDto> followUps,
            OffsetDateTime computedAt) implements Serializable {
    }
}
