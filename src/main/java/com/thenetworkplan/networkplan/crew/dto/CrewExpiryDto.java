package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One thing that expires on one crew member.
 *
 * <p>Licences, medicals, recurrent training and every qualification share this
 * shape, because the question an OCC asks is the same for all of them: who,
 * what, when, and how many days are left. Splitting them into two lists would
 * mean two sort orders and two definitions of "soon".
 *
 * @param kind      LICENCE, MEDICAL, TRAINING, or a qualification kind
 * @param subject   what it bears on: an aircraft type, a level, or nothing
 * @param expiresOn null when the crew file has no date — which is a finding,
 *                  not a blank
 * @param daysRemaining negative once the date has passed; null when unknown
 * @param status    VALID / EXPIRING / EXPIRED / UNKNOWN, from the one rule
 *                  that judges every document in the product
 */
public record CrewExpiryDto(
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        String baseIcao,
        String kind,
        String subject,
        LocalDate expiresOn,
        Long daysRemaining,
        String status,
        String reference) implements Serializable {
}
