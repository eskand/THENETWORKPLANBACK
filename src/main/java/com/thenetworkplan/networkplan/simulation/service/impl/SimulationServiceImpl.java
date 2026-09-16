package com.thenetworkplan.networkplan.simulation.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.simulation.domain.SimulationEvent;
import com.thenetworkplan.networkplan.simulation.domain.SimulationScenario;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.RunScenarioCommand;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.ScenarioDto;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.SimulationBoardDto;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.SimulationEventDto;
import com.thenetworkplan.networkplan.simulation.repository.SimulationEventRepository;
import com.thenetworkplan.networkplan.simulation.repository.SimulationScenarioRepository;
import com.thenetworkplan.networkplan.simulation.service.SimulationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulation Center.
 *
 * <p>Note what this class does not have: no {@code LegService}, no
 * {@code AircraftService}, no repository outside its own package. Running a
 * scenario stamps a date on it; it cannot delay a flight, ground a tail or
 * write anything an OCC would read as real.
 */
@Service
@Transactional(readOnly = true)
public class SimulationServiceImpl implements SimulationService {

    private final SimulationScenarioRepository scenarioRepository;
    private final SimulationEventRepository eventRepository;

    public SimulationServiceImpl(SimulationScenarioRepository scenarioRepository,
                                 SimulationEventRepository eventRepository) {
        this.scenarioRepository = scenarioRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public SimulationBoardDto findBoard(UUID tenantId) {
        List<SimulationScenario> scenarios = scenarioRepository.findByTenantIdOrderByCodeAsc(tenantId);
        if (scenarios.isEmpty()) {
            return new SimulationBoardDto(List.of(), 0, 0, 0, true, OffsetDateTime.now(ZoneOffset.UTC));
        }

        Map<UUID, List<SimulationEventDto>> byScenario = eventsFor(tenantId, scenarios);

        List<ScenarioDto> rows = new ArrayList<>(scenarios.size());
        int draft = 0;
        int ready = 0;
        int run = 0;
        for (SimulationScenario scenario : scenarios) {
            rows.add(toDto(scenario, byScenario.getOrDefault(scenario.getId(), List.of())));
            switch (scenario.getStatus()) {
                case "DRAFT" -> draft++;
                case "READY" -> ready++;
                case "RUN" -> run++;
                default -> { }
            }
        }
        return new SimulationBoardDto(rows, draft, ready, run, true, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public ScenarioDto findScenario(UUID tenantId, UUID scenarioId) {
        SimulationScenario scenario = require(tenantId, scenarioId);
        return toDto(scenario, eventsFor(tenantId, List.of(scenario))
                .getOrDefault(scenarioId, List.of()));
    }

    @Override
    @Transactional
    public ScenarioDto run(UUID tenantId, UUID scenarioId, RunScenarioCommand command, UUID actorId) {
        SimulationScenario scenario = require(tenantId, scenarioId);
        scenario.setLastRunAt(OffsetDateTime.now(ZoneOffset.UTC));
        scenario.setLastRunBy(actorId);
        scenario.setStatus("RUN");
        if (command != null && command.remark() != null && !command.remark().isBlank()) {
            scenario.setNarrative(scenario.getNarrative() + "\n\nRun note: " + command.remark());
        }
        SimulationScenario saved = scenarioRepository.save(scenario);
        return toDto(saved, eventsFor(tenantId, List.of(saved)).getOrDefault(scenarioId, List.of()));
    }

    // ----------------------------------------------------------------

    private Map<UUID, List<SimulationEventDto>> eventsFor(UUID tenantId, List<SimulationScenario> scenarios) {
        Map<UUID, List<SimulationEventDto>> byScenario = new HashMap<>();
        for (SimulationEvent event : eventRepository.findByScenarioIds(
                tenantId, scenarios.stream().map(SimulationScenario::getId).toList())) {
            byScenario.computeIfAbsent(event.getScenario().getId(), key -> new ArrayList<>())
                    .add(new SimulationEventDto(
                            event.getId(), event.getSequenceNo(), event.getOffsetMinutes(),
                            event.getKind(), event.getRegistration(), event.getFlightNo(),
                            event.getStationIcao(), event.getDetail(), event.getExpectedAction()));
        }
        return byScenario;
    }

    private ScenarioDto toDto(SimulationScenario scenario, List<SimulationEventDto> events) {
        int decisions = (int) events.stream()
                .filter(event -> "DECISION_POINT".equals(event.kind()))
                .count();
        int duration = events.stream()
                .mapToInt(SimulationEventDto::offsetMinutes)
                .max()
                .orElse(0);
        return new ScenarioDto(
                scenario.getId(), scenario.getCode(), scenario.getTitle(), scenario.getKind(),
                scenario.getNarrative(), scenario.getBaselineDate(), scenario.getStatus(),
                scenario.getLastRunAt(), events, decisions, duration);
    }

    private SimulationScenario require(UUID tenantId, UUID scenarioId) {
        return scenarioRepository.findByTenantIdAndId(tenantId, scenarioId)
                .orElseThrow(() -> ResourceNotFoundException.of("Scenario", scenarioId));
    }
}
