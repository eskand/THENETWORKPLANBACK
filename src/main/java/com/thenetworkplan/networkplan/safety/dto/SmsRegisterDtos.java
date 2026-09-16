package com.thenetworkplan.networkplan.safety.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The read models of the hazard register, the experience library and the
 * notification centre.
 *
 * <p>Every index and every percentage here is computed at read time. None is
 * stored: a risk index stored beside its own severity and likelihood is a third
 * number that can disagree with the two it was made from.
 */
public final class SmsRegisterDtos {

    private SmsRegisterDtos() {
    }

    /** One barrier. */
    public record ControlDto(
            UUID id,
            String description,
            /** preventive — stops the event; recovery — limits what follows. */
            String controlType,
            String owner,
            String status) implements Serializable {
    }

    /** One hazard, with what the barriers actually buy. */
    public record HazardDto(
            UUID id,
            String reference,
            String hazard,
            String consequence,
            String domain,
            String category,
            String identification,
            String severityInitial,
            int likelihoodInitial,
            int indexInitial,
            String severityResidual,
            int likelihoodResidual,
            int indexResidual,
            /** How much of the risk the barriers remove. Zero when nothing changed. */
            int reductionPercent,
            String bandInitial,
            String bandResidual,
            String owner,
            LocalDate reviewOn,
            /** Negative once the review date has passed. Derived, never stored. */
            Long daysToReview,
            boolean reviewOverdue,
            String status,
            String notes,
            List<ControlDto> controls,
            int controlsInPlace,
            /** The occurrences this hazard was identified from, by reference. */
            List<String> linkedOccurrences) implements Serializable {
    }

    /** One cell of the 5×5 matrix, with what sits in it. */
    public record MatrixCellDto(
            String severity,
            int likelihood,
            int index,
            String band,
            String colour,
            String action,
            int population) implements Serializable {
    }

    /** The register: the matrix and the hazards under it. */
    public record HazardRegisterDto(
            List<HazardDto> hazards,
            /** The matrix as the operator's own table defines it, not as code. */
            List<MatrixCellDto> matrixInitial,
            List<MatrixCellDto> matrixResidual,
            int open,
            int mitigating,
            int monitored,
            int reviewOverdue,
            OffsetDateTime computedAt) implements Serializable {
    }

    /** One published experience report. */
    public record RexDto(
            UUID id,
            String reference,
            String title,
            String category,
            String phase,
            String aircraftType,
            String location,
            String narrative,
            String recommendation,
            /** Null when the report is anonymous — the column itself is empty. */
            String authorName,
            String authorRole,
            String attribution,
            String scope,
            LocalDate publishedOn,
            String status,
            List<String> lessons,
            int reads,
            /** Whether this reader has read it. */
            boolean readByMe) implements Serializable {
    }

    public record NotificationDto(
            UUID id,
            OffsetDateTime at,
            String kind,
            String title,
            String body,
            String severity,
            String entityRef,
            String domain,
            boolean read) implements Serializable {
    }

    public record NotificationCentreDto(
            List<NotificationDto> notifications,
            long unread,
            OffsetDateTime computedAt) implements Serializable {
    }

    /**
     * One question the Safety Manager has put to a reporter.
     *
     * <p>This is the reporter's "Action required" list: a report they filed that
     * cannot move until they answer.
     */
    public record QueryDto(
            UUID occurrenceId,
            String reference,
            String title,
            OffsetDateTime occurredAt,
            String query,
            OffsetDateTime askedAt,
            String askedBy,
            String answer,
            OffsetDateTime answeredAt) implements Serializable {
    }
}
