package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One pilot's low-visibility approach category.
 *
 * <p>{@code approachCategory} is never null: a pilot with no LVO qualification
 * on file is CAT I, the company standard, and saying so plainly is more use
 * than a dash. {@code graded} distinguishes the two cases — CAT I because
 * somebody wrote it, or CAT I because nothing above it was ever recorded.
 *
 * <p>{@code validTo} and {@code reference} come from the LVO qualification and
 * are null for an ungraded pilot. They are the reason the category lives on a
 * qualification rather than on the person: CAT IIIB lapses, and a category
 * with no expiry would go on asserting a training nobody renewed.
 */
public record OpsQualificationDto(
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        List<String> typeRatings,
        String approachCategory,
        boolean graded,
        LocalDate validFrom,
        LocalDate validTo,
        /** VALID / EXPIRING / EXPIRED / UNKNOWN, from the shared document rule. */
        String status,
        String reference) implements Serializable {
}
