package com.thenetworkplan.networkplan.camo.web;

import com.thenetworkplan.networkplan.camo.dto.AircraftCamoDto;
import com.thenetworkplan.networkplan.camo.dto.CompleteTaskCommand;
import com.thenetworkplan.networkplan.camo.dto.DueItemDto;
import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.dto.LifeLimitedPartDto;
import com.thenetworkplan.networkplan.camo.dto.WorkOrderDto;
import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API30 — CAMO: fleet airworthiness, due list, utilisation. */
@RestController
@RequestMapping("/v1/camo")
public class CamoController {

    private final CamoService camoService;

    public CamoController(CamoService camoService) {
        this.camoService = camoService;
    }

    @GetMapping("/fleet")
    public List<FleetStatusRowDto> fleet() {
        return camoService.findFleetStatus(TenantContext.require());
    }

    /** Ninety days by default: the horizon a maintenance plan is built on. */
    @GetMapping("/due-list")
    public List<DueItemDto> dueList(@RequestParam(name = "horizonDays", defaultValue = "90") int horizonDays) {
        return camoService.findDueList(TenantContext.require(), horizonDays);
    }

    /** Shortest remaining life first — the tab is a queue, not a catalogue. */
    @GetMapping("/life-limited-parts")
    public List<LifeLimitedPartDto> lifeLimitedParts() {
        return camoService.findLifeLimitedParts(TenantContext.require());
    }

    @GetMapping("/work-orders")
    public List<WorkOrderDto> workOrders() {
        return camoService.findOpenWorkOrders(TenantContext.require());
    }

    @GetMapping("/aircraft/{id}")
    public AircraftCamoDto aircraft(@PathVariable UUID id) {
        return camoService.findAircraft(TenantContext.require(), id);
    }

    @PatchMapping("/tasks/{id}/complete")
    public DueItemDto completeTask(@PathVariable UUID id, @Valid @RequestBody CompleteTaskCommand command) {
        return camoService.completeTask(TenantContext.require(), id, command);
    }

    @PostMapping("/programme-tasks/{id}/rollout")
    public Map<String, Integer> rollout(@PathVariable UUID id) {
        return Map.of("created", camoService.rolloutProgrammeTask(TenantContext.require(), id));
    }
}
