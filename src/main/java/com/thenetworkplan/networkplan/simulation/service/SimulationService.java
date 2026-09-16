package com.thenetworkplan.networkplan.simulation.service;

import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.RunScenarioCommand;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.ScenarioDto;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.SimulationBoardDto;
import java.util.UUID;

/**
 * Simulation Center.
 *
 * <p>Deliberately narrow: it reads and writes {@code planning.simulation_*}
 * and nothing else. There is no method here that touches DOM1, and no
 * dependency on any service that could — that is the fix to the audit's
 * finding that the prototype's simulator wrote onto the live timeline.
 */
public interface SimulationService {

    SimulationBoardDto findBoard(UUID tenantId);

    ScenarioDto findScenario(UUID tenantId, UUID scenarioId);

    /** Records that the scenario was played. It changes nothing operational. */
    ScenarioDto run(UUID tenantId, UUID scenarioId, RunScenarioCommand command, UUID actorId);
}
