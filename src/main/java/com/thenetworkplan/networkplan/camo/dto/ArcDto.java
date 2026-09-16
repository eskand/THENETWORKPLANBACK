package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One airworthiness review certificate, as the screen needs it.
 *
 * <p>{@code daysLeft} and {@code verdict} are computed when the record is
 * built, from the expiry date and the day asked about. They are carried here
 * rather than recomputed in the browser so that the figure the planner sees and
 * the figure a report prints are the same figure.
 */
public record ArcDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String certificateNo,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Long daysLeft,
        String verdict,
        String reviewBasis,
        String reviewBasisLabel,
        String reviewedBy,
        String reviewerApprovalNo,
        String cofaRef,
        boolean inForce) implements Serializable {
}
