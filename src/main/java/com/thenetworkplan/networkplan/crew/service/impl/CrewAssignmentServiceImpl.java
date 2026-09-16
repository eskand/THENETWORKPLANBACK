package com.thenetworkplan.networkplan.crew.service.impl;

import com.thenetworkplan.networkplan.crew.domain.CrewAssignment;
import com.thenetworkplan.networkplan.crew.domain.CrewSeat;
import com.thenetworkplan.networkplan.crew.domain.FtlVerdict;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.mapper.CrewMapper;
import com.thenetworkplan.networkplan.crew.repository.CrewAssignmentRepository;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CrewAssignmentServiceImpl implements CrewAssignmentService {

    /**
     * Flight-deck seats a leg needs. Two for every type in this fleet; when
     * single-pilot operations or a required engineer seat arrive, this becomes a
     * lookup on the aircraft type rather than a constant.
     */
    private static final int MINIMUM_FLIGHT_DECK_SEATS = 2;

    private final CrewAssignmentRepository assignmentRepository;
    private final CrewDocumentChecker documentChecker;
    private final CrewMapper mapper;

    public CrewAssignmentServiceImpl(CrewAssignmentRepository assignmentRepository,
                                     CrewDocumentChecker documentChecker,
                                     CrewMapper mapper) {
        this.assignmentRepository = assignmentRepository;
        this.documentChecker = documentChecker;
        this.mapper = mapper;
    }

    @Override
    public Map<UUID, LegCrewDto> findByLegIds(UUID tenantId, Collection<UUID> legIds, LocalDate flightDate) {
        if (legIds == null || legIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<CrewAssignment>> byLeg = new LinkedHashMap<>();
        for (CrewAssignment assignment : assignmentRepository.findByLegIds(tenantId, legIds)) {
            byLeg.computeIfAbsent(assignment.getLegId(), key -> new ArrayList<>()).add(assignment);
        }
        Map<UUID, LegCrewDto> result = new LinkedHashMap<>();
        byLeg.forEach((legId, assignments) -> result.put(legId, fold(legId, assignments, flightDate)));
        return result;
    }

    @Override
    public LegCrewDto findByLeg(UUID tenantId, UUID legId, LocalDate flightDate) {
        List<CrewAssignment> assignments = assignmentRepository.findByLeg(tenantId, legId);
        if (assignments.isEmpty()) {
            return LegCrewDto.unassigned(legId, MINIMUM_FLIGHT_DECK_SEATS);
        }
        return fold(legId, assignments, flightDate);
    }

    /** Folds the assignments of one leg into the single verdict the board shows. */
    private LegCrewDto fold(UUID legId, List<CrewAssignment> assignments, LocalDate flightDate) {
        List<CrewMemberDto> members = new ArrayList<>(assignments.size());
        FtlVerdict worstFtl = FtlVerdict.OK;
        DocumentValidity worstDocuments = DocumentValidity.VALID;
        int flightDeckSeats = 0;

        for (CrewAssignment assignment : assignments) {
            DocumentValidity validity = documentChecker.check(assignment.getPerson(), flightDate);
            members.add(mapper.toDto(assignment, flightDate, validity));

            if (assignment.getFtlVerdict().severity() > worstFtl.severity()) {
                worstFtl = assignment.getFtlVerdict();
            }
            if (validity.severity() > worstDocuments.severity()) {
                worstDocuments = validity;
            }
            if (assignment.getSeat().isFlightDeck()) {
                flightDeckSeats++;
            }
        }

        boolean complete = flightDeckSeats >= MINIMUM_FLIGHT_DECK_SEATS;
        return new LegCrewDto(legId, List.copyOf(members), flightDeckSeats,
                MINIMUM_FLIGHT_DECK_SEATS, complete, worstFtl.name(), worstDocuments.name());
    }

    /** Kept for readability at call sites that only care about the flight deck. */
    static boolean isFlightDeck(CrewSeat seat) {
        return seat.isFlightDeck();
    }
}
