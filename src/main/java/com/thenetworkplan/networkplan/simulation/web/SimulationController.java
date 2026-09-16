package com.thenetworkplan.networkplan.simulation.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.RunScenarioCommand;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.ScenarioDto;
import com.thenetworkplan.networkplan.simulation.dto.SimulationDtos.SimulationBoardDto;
import com.thenetworkplan.networkplan.simulation.service.SimulationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API71 — Simulation Center. Reads and writes nothing outside planning. */
@RestController
@RequestMapping("/v1/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/board")
    public SimulationBoardDto board() {
        return simulationService.findBoard(TenantContext.require());
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioDto scenario(@PathVariable UUID id) {
        return simulationService.findScenario(TenantContext.require(), id);
    }

    @PostMapping("/scenarios/{id}/run")
    public ScenarioDto run(@PathVariable UUID id,
                           @RequestBody(required = false) RunScenarioCommand command,
                           @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return simulationService.run(TenantContext.require(), id, command, actorId);
    }
}
