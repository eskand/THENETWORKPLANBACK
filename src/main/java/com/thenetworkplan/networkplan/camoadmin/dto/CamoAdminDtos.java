package com.thenetworkplan.networkplan.camoadmin.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The read models of the CAMO back office.
 *
 * <p>Every figure is derived at read time. None of the life remaining, days to
 * expiry or compliance percentages is stored anywhere: a stored figure is right
 * on the day it is written and quietly wrong on every day after, and this is
 * the record an authority audits.
 */
public final class CamoAdminDtos {

    private CamoAdminDtos() {
    }

    /** One component of the register, with what is left of its life. */
    public record ComponentDto(
            UUID id,
            String category,
            UUID aircraftId,
            String registration,
            String name,
            String ataChapter,
            String partNumber,
            String serialNumber,
            String position,
            LocalDate installDate,
            LocalDate removalDate,
            BigDecimal tsn,
            Integer csn,
            BigDecimal tso,
            Integer cso,
            BigDecimal lifeLimitHours,
            Integer lifeLimitCycles,
            LocalDate calendarLimit,
            /** Limit minus hours flown. Null when the part carries no hour limit. */
            BigDecimal hoursRemaining,
            Integer cyclesRemaining,
            /** The tightest of the three limits, as a percentage of life used. */
            Integer lifeUsedPercent,
            LocalDate overhaulDue,
            LocalDate nextInspection,
            BigDecimal egtMargin,
            BigDecimal oilConsumption,
            String status,
            String notes) implements Serializable {
    }

    /** One document, with the clock that applies to it. */
    public record DocumentDto(
            UUID id,
            String category,
            String title,
            String reference,
            UUID aircraftId,
            String registration,
            LocalDate issueDate,
            LocalDate expiryDate,
            /** Null when the document has no expiry — which is not the same as unknown. */
            Long daysToExpiry,
            /** CURRENT / EXPIRING / EXPIRED / NO_EXPIRY. */
            String expiryStatus,
            String issuedBy,
            String status,
            String notes) implements Serializable {
    }

    /**
     * One person who may use the platform.
     *
     * <p>{@code airworthinessAuthority} is derived from the CAMO role, not
     * stored: membership of the department is what carries the authority, and
     * a stored flag beside the role would eventually contradict it.
     */
    public record UserDto(
            UUID id,
            String login,
            String displayName,
            String role,
            String camoRole,
            String email,
            boolean airworthinessAuthority,
            boolean active) implements Serializable {
    }

    public record AuditEventDto(
            Long id,
            OffsetDateTime at,
            String entity,
            String entityId,
            String entityLabel,
            String action,
            String field,
            String oldValue,
            String newValue,
            String actorName,
            String actorRole,
            String reason) implements Serializable {
    }

    /**
     * One thing wrong with the record, found by looking rather than by being told.
     *
     * <p>Alerts are not stored. Every one is recomputed from the rows: an ARC
     * that expired last night has to appear this morning without anybody having
     * written an alert row for it.
     */
    public record AlertDto(
            /** CRITICAL / WARNING / INFO. */
            String severity,
            String area,
            String subject,
            String detail,
            /** Where to go to deal with it. */
            String destination,
            UUID entityId) implements Serializable {
    }

    /** The CAMO back office at a glance. */
    public record CamoAdminBoardDto(
            int aircraft,
            int aircraftServiceable,
            int aircraftAog,
            int arcInForce,
            int arcExpiringWithin30Days,
            int arcExpired,
            int directivesOpen,
            int directivesOverdue,
            int programmeTasks,
            int workOrdersOpen,
            int defectsOpen,
            int deferralsInForce,
            int components,
            int componentsUnserviceable,
            int documents,
            int documentsExpired,
            int documentsExpiringWithin30Days,
            /** How much of the record carries a provenance other than "manual". */
            int recordsFromImport,
            List<AlertDto> alerts,
            OffsetDateTime computedAt) implements Serializable {
    }
}
