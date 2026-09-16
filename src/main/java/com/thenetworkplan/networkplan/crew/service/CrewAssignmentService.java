package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** DOM4 read surface consumed by dispatch (API10). */
public interface CrewAssignmentService {

    /**
     * Crew picture for a set of legs, resolved in one query.
     *
     * @param flightDate date the documents are checked against
     * @return leg id to crew picture; legs with no assignment are absent
     */
    Map<UUID, LegCrewDto> findByLegIds(UUID tenantId, Collection<UUID> legIds, LocalDate flightDate);

    LegCrewDto findByLeg(UUID tenantId, UUID legId, LocalDate flightDate);
}
