package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * One cell of the compliance matrix: what one person holds for one course.
 *
 * <p>{@code MISSING} is its own state and is not folded into {@code EXPIRED}: a
 * crew member who never did the course and one whose certificate lapsed need
 * different actions, and the audit found the prototype showing neither.
 */
public record ComplianceCellDto(
        String courseCode,
        /** VALID / EXPIRING / EXPIRED / UNKNOWN / MISSING */
        String status,
        LocalDate completedOn,
        LocalDate validTo) implements Serializable {

    public static ComplianceCellDto missing(String courseCode) {
        return new ComplianceCellDto(courseCode, "MISSING", null, null);
    }
}
