package com.thenetworkplan.networkplan.sim.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.GenerateScenarioCommand;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.GeneratorBoardDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDetailDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDto;
import com.thenetworkplan.networkplan.sim.service.ScenarioSandboxService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Simulation Center — scenario preparation. Sandbox only. */
@RestController
@RequestMapping("/v1/simulation/sandbox")
public class ScenarioSandboxController {

    private final ScenarioSandboxService simulationService;

    public ScenarioSandboxController(ScenarioSandboxService simulationService) {
        this.simulationService = simulationService;
    }

    /** The generator tab: the baseline, the presets, the injectors, recent scenarios. */
    @GetMapping("/generator")
    public GeneratorBoardDto generator() {
        return simulationService.findGeneratorBoard(TenantContext.require());
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDetailDto generate(@Valid @RequestBody GenerateScenarioCommand command) {
        return simulationService.generate(TenantContext.require(), command);
    }

    @GetMapping("/scenarios")
    public List<ScenarioDto> scenarios() {
        return simulationService.findScenarios(TenantContext.require());
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioDetailDto scenario(@PathVariable UUID id) {
        return simulationService.findScenario(TenantContext.require(), id);
    }

    /** Reset: drops the scenario and everything in it. Cannot reach the live plan. */
    @DeleteMapping("/scenarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        simulationService.delete(TenantContext.require(), id);
    }
}
