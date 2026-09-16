package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.tripsupport.dto.CreatePermitRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.PermitRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API5 / API6 — overflight and landing permits of a leg. */
public interface PermitService {

    Map<UUID, LegPermitsSummary> summariseByLegIds(UUID tenantId, Collection<UUID> legIds);

    long countOutstanding(UUID tenantId, Collection<UUID> legIds);

    LegPermitsDto findByLeg(UUID tenantId, UUID legId);

    /**
     * Permit requests not confirmed over a window, for reporting.
     *
     * <p>DOM2 answers the question rather than letting the reporting module
     * read {@code tripsupport.permit_requests}.
     */
    java.util.List<PermitRequestDto> findOutstandingInWindow(UUID tenantId,
                                                             java.time.LocalDate from,
                                                             java.time.LocalDate to);

    PermitRequestDto create(UUID tenantId, UUID legId, CreatePermitRequestCommand command);

    PermitRequestDto updateStatus(UUID tenantId, UUID requestId, UpdateRequestStatusCommand command);
}
