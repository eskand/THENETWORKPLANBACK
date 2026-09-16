package com.thenetworkplan.networkplan.simulation.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The read models of the Simulation Center. */
public final class SimulationDtos {

    private SimulationDtos() {
    }

    public record SimulationEventDto(
            UUID id,
            int sequenceNo,
            int offsetMinutes,
            String kind,
            String registration,
            String flightNo,
            String stationIcao,
            String detail,
            String expectedAction) implements Serializable {
    }

    public record ScenarioDto(
            UUID id,
            String code,
            String title,
            String kind,
            String narrative,
            LocalDate baselineDate,
            String status,
            OffsetDateTime lastRunAt,
            List<SimulationEventDto> events,
            int decisionPoints,
            int durationMinutes) implements Serializable {
    }

    public record SimulationBoardDto(
            List<ScenarioDto> scenarios,
            int draft,
            int ready,
            int run,
            /** Always true: the module has no write path into ops, by construction. */
            boolean isolatedFromLiveOperations,
            OffsetDateTime computedAt) implements Serializable {
    }

    public record RunScenarioCommand(String remark) implements Serializable {
    }
}
