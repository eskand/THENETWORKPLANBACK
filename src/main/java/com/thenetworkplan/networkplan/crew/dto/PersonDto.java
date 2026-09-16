package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One row of the Crew Management list.
 *
 * <p>Everything here is either a stored column or a count the database
 * answered: {@code documentStatus} comes from {@code CrewDocumentChecker}, the
 * three cumulative figures come from {@code sum} over {@code crew.duty_periods},
 * and {@code absentToday} from an overlap test on {@code crew.absences}.
 */
public record PersonDto(
        UUID id,
        String staffNo,
        String firstName,
        String lastName,
        String fullName,
        String mainRole,
        String baseIcao,
        LocalDate licenceExpiry,
        LocalDate medicalExpiry,
        LocalDate trainingExpiry,
        /** VALID / EXPIRING / EXPIRED / UNKNOWN — the worst of the three above. */
        String documentStatus,
        List<String> typeRatings,
        long blockMinutes7d,
        long blockMinutes28d,
        long blockMinutes365d,
        String absentToday,
        boolean active) implements Serializable {
}
