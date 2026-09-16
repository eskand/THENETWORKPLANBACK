package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.tripsupport.dto.CreateServiceRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API6 — ground services of a leg. */
public interface GroundServiceService {

    /** Readiness per leg, computed by the database in one grouped query. */
    Map<UUID, LegServicesSummary> summariseByLegIds(UUID tenantId, Collection<UUID> legIds);

    LegServicesDto findByLeg(UUID tenantId, UUID legId);

    ServiceRequestDto create(UUID tenantId, UUID legId, CreateServiceRequestCommand command);

    ServiceRequestDto updateStatus(UUID tenantId, UUID requestId, UpdateRequestStatusCommand command);
}
