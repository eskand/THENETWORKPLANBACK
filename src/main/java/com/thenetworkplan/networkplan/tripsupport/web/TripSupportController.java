package com.thenetworkplan.networkplan.tripsupport.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.tripsupport.dto.CreatePermitRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.CreateServiceRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.PermitRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API5 / API6 — permits and ground services of a leg. */
@RestController
@RequestMapping("/v1")
public class TripSupportController {

    private final PermitService permitService;
    private final GroundServiceService groundServiceService;

    public TripSupportController(PermitService permitService, GroundServiceService groundServiceService) {
        this.permitService = permitService;
        this.groundServiceService = groundServiceService;
    }

    @GetMapping("/legs/{legId}/permits")
    public LegPermitsDto permits(@PathVariable UUID legId) {
        return permitService.findByLeg(TenantContext.require(), legId);
    }

    @PostMapping("/legs/{legId}/permit-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public PermitRequestDto createPermitRequest(@PathVariable UUID legId,
                                                @Valid @RequestBody CreatePermitRequestCommand command) {
        return permitService.create(TenantContext.require(), legId, command);
    }

    @PatchMapping("/permit-requests/{requestId}")
    public PermitRequestDto updatePermitRequest(@PathVariable UUID requestId,
                                                 @Valid @RequestBody UpdateRequestStatusCommand command) {
        return permitService.updateStatus(TenantContext.require(), requestId, command);
    }

    @GetMapping("/legs/{legId}/services")
    public LegServicesDto services(@PathVariable UUID legId) {
        return groundServiceService.findByLeg(TenantContext.require(), legId);
    }

    @PostMapping("/legs/{legId}/service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestDto createServiceRequest(@PathVariable UUID legId,
                                                   @Valid @RequestBody CreateServiceRequestCommand command) {
        return groundServiceService.create(TenantContext.require(), legId, command);
    }

    @PatchMapping("/service-requests/{requestId}")
    public ServiceRequestDto updateServiceRequest(@PathVariable UUID requestId,
                                                   @Valid @RequestBody UpdateRequestStatusCommand command) {
        return groundServiceService.updateStatus(TenantContext.require(), requestId, command);
    }
}
