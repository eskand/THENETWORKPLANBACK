package com.thenetworkplan.networkplan.sim.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The read models of the Simulation Center. */
public final class SimDtos {

    private SimDtos() {
    }

    /** One difficulty card, with what it asks for and what it can place today. */
    public record DifficultyDto(
            String name,
            String description,
            Map<String, Integer> counts,
            int requestedAnomalies,
            /** How many of those the implemented injectors can actually place. */
            int placeableAnomalies) implements Serializable {
    }

    /** One injector the API knows about. */
    public record InjectorDto(
            String key,
            String anomalyType,
            String label,
            String severity,
            String expectedFix,
            boolean implemented) implements Serializable {
    }

    /**
     * The sandbox baseline: what the copied plan holds before anything is put wrong.
     *
     * <p>Counted from the operational plan over the horizon, not declared:
     * "120 revenue sectors" has to be 120 sectors that exist.
     */
    public record BaselineDto(
            int aircraft,
            int revenueSectors,
            int crewMembers,
            int horizonDays,
            LocalDate from,
            LocalDate to,
            long blockMinutes) implements Serializable {
    }

    public record AnomalyDto(
            UUID id,
            String reference,
            String anomalyType,
            String label,
            String severity,
            String expectedFix,
            String registration,
            Short dayOffset,
            String note,
            List<UUID> legIds) implements Serializable {
    }

    public record ScenarioLegDto(
            UUID id,
            String registration,
            String icaoType,
            String flightNo,
            String depIcao,
            String arrIcao,
            OffsetDateTime std,
            OffsetDateTime sta,
            String flightType,
            int paxCount,
            String status,
            Integer delayMinutes,
            boolean injected) implements Serializable {
    }

    /** One line of the "requested against applied" report. */
    public record InjectionResultDto(
            String key,
            String label,
            int requested,
            int applied,
            boolean implemented) implements Serializable {
    }

    public record ScenarioDto(
            UUID id,
            String reference,
            String name,
            String difficulty,
            LocalDate horizonFrom,
            short horizonDays,
            long randomSeed,
            String status,
            OffsetDateTime createdAt,
            BaselineDto baseline,
            List<InjectionResultDto> injection,
            int anomalyCount,
            int legCount,
            int injectedLegCount) implements Serializable {
    }

    /** A scenario opened in full: its legs and its anomalies. */
    public record ScenarioDetailDto(
            ScenarioDto scenario,
            List<ScenarioLegDto> legs,
            List<AnomalyDto> anomalies) implements Serializable {
    }

    /** What the Scenario Generator tab needs before anything is generated. */
    public record GeneratorBoardDto(
            BaselineDto baseline,
            List<DifficultyDto> difficulties,
            List<InjectorDto> injectors,
            List<ScenarioDto> recent) implements Serializable {
    }

    /**
     * Asking for a scenario.
     *
     * @param counts overrides the preset, injector key to count; used by CUSTOM
     * @param seed   pass the seed of an earlier scenario to reproduce it exactly
     */
    public record GenerateScenarioCommand(
            String difficulty,
            @Min(1) @Max(14) Integer horizonDays,
            Map<String, Integer> counts,
            Long seed,
            String name) {
    }
}
