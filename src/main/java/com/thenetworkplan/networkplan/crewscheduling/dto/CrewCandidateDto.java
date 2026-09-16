package com.thenetworkplan.networkplan.crewscheduling.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One crew member offered for a seat, with the reason for the verdict.
 *
 * <p>Everyone qualified appears, including those who cannot be assigned: a
 * planner needs to see that the only rated captain is on sick leave, not an
 * empty list. That is why {@code availability} and {@code reason} travel with
 * the row rather than the row being filtered out.
 */
public record CrewCandidateDto(
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        String baseIcao,
        List<String> typeRatings,
        String availability,
        String reason,
        String documentStatus,
        OffsetDateTime lastOffDutyAt,
        Long restMinutes,
        long blockMinutes28d) implements Serializable {
}
