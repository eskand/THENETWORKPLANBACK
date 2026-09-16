package com.thenetworkplan.networkplan.sim.service;

import com.thenetworkplan.networkplan.sim.dto.SimDtos.GenerateScenarioCommand;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.GeneratorBoardDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDetailDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDto;
import java.util.List;
import java.util.UUID;

/**
 * The Simulation Center.
 *
 * <p>Everything this service writes lands in the sim schema, which references
 * no operational table. The sandbox promise on the screen is enforced by the
 * database, not by care taken in the code.
 */
public interface ScenarioSandboxService {

    GeneratorBoardDto findGeneratorBoard(UUID tenantId);

    ScenarioDetailDto generate(UUID tenantId, GenerateScenarioCommand command);

    ScenarioDetailDto findScenario(UUID tenantId, UUID scenarioId);

    List<ScenarioDto> findScenarios(UUID tenantId);

    void delete(UUID tenantId, UUID scenarioId);
}
