package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.tripsupport.dto.CreateServiceRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateServiceRequestDetailsCommand;
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

    /** Corriger le type ou le fournisseur d'une ligne non engagee. */
    ServiceRequestDto updateDetails(UUID tenantId, UUID requestId, UpdateServiceRequestDetailsCommand command);

    /**
     * Removing a row the dispatcher added by mistake.
     *
     * <p>Only a draft can go: once a request has left the product the record of
     * it is the audit trail, and the annexe's cross deleted it from a browser
     * store that nobody could audit afterwards.
     */
    void delete(UUID tenantId, UUID requestId);
}
