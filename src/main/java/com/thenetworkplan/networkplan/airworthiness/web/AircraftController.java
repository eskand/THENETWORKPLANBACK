package com.thenetworkplan.networkplan.airworthiness.web;

import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.AirworthinessSnapshotDto;
import com.thenetworkplan.networkplan.airworthiness.dto.ChangeAircraftStatusCommand;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API12 — fleet, airworthiness status, open MEL. */
@RestController
@RequestMapping("/v1/aircraft")
public class AircraftController {

    private final AircraftService aircraftService;

    public AircraftController(AircraftService aircraftService) {
        this.aircraftService = aircraftService;
    }

    @GetMapping
    public List<AircraftDto> fleet(@RequestParam(name = "grounded", defaultValue = "false") boolean groundedOnly) {
        UUID tenantId = TenantContext.require();
        return groundedOnly ? aircraftService.findGrounded(tenantId) : aircraftService.findFleet(tenantId);
    }

    @GetMapping("/{id}/airworthiness")
    public AirworthinessSnapshotDto airworthiness(@PathVariable UUID id) {
        return aircraftService.findAirworthiness(TenantContext.require(), id);
    }

    @PatchMapping("/{id}/status")
    public AircraftDto changeStatus(@PathVariable UUID id,
                                    @Valid @RequestBody ChangeAircraftStatusCommand command) {
        return aircraftService.changeStatus(TenantContext.require(), id, command);
    }
}
