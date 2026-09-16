package com.thenetworkplan.networkplan.safety.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The read models of the three safety screens. */
public final class SafetyDtos {

    private SafetyDtos() {
    }

    public record ActionDto(
            UUID id,
            UUID occurrenceId,
            String occurrenceReference,
            String reference,
            String title,
            String detail,
            UUID ownerUserId,
            LocalDate dueOn,
            Long daysToDue,
            String status,
            LocalDate completedOn,
            String effectiveness,
            boolean overdue) implements Serializable {
    }

    /**
     * One occurrence.
     *
     * <p>{@code reportedByName} is null when the report is anonymous — the row
     * keeps the reporter, the read model does not return them. That is what a
     * just culture requires of the product, and it is enforced in the mapper
     * so no screen can bypass it.
     */
    public record OccurrenceDto(
            UUID id,
            String reference,
            OffsetDateTime occurredAt,
            OffsetDateTime reportedAt,
            String reportedByName,
            boolean anonymous,
            UUID legId,
            String registration,
            String stationIcao,
            String category,
            String title,
            String narrative,
            String phaseOfFlight,
            String eccairsEventType,
            String eccairsOccurrenceClass,
            OffsetDateTime eccairsExportedAt,
            String eccairsReference,
            String riskSeverity,
            Integer riskProbability,
            String riskLevel,
            OffsetDateTime riskAssessedAt,
            String status,
            OffsetDateTime closedAt,
            List<ActionDto> actions,
            int openActions) implements Serializable {
    }

    public record RiskCellDto(
            String severity,
            int probability,
            String riskLevel,
            String actionRequired,
            /** How many open occurrences currently sit in this cell. */
            int occurrences) implements Serializable {
    }

    public record SafetyBoardDto(
            List<OccurrenceDto> occurrences,
            List<RiskCellDto> matrix,
            int total,
            int reported,
            int underReview,
            int assessed,
            int closed,
            int unacceptable,
            int openActions,
            int overdueActions,
            int notAssessed,
            OffsetDateTime computedAt) implements Serializable {
    }

    /** Safety Reports: the filing view, ECCAIRS included. */
    public record ReportingSummaryDto(
            List<OccurrenceDto> occurrences,
            Map<String, Long> byCategory,
            Map<String, Long> byMonth,
            Map<String, Long> byRiskLevel,
            int total,
            int filedWithAuthority,
            int awaitingFiling,
            int anonymousReports,
            OffsetDateTime computedAt) implements Serializable {
    }

    public record CampaignDto(
            UUID id,
            String reference,
            /** ALERT, BULLETIN, LESSON, POLICY — an alert is not a policy. */
            String kind,
            String kindLabel,
            /** The colour the kind carries across every screen that shows it. */
            String kindColour,
            String authorName,
            LocalDate publishedOn,
            String title,
            String theme,
            String message,
            LocalDate startsOn,
            LocalDate endsOn,
            String audience,
            String status,
            boolean acknowledgementRequired,
            int audienceSize,
            int acknowledgements,
            /** Null when no acknowledgement is required: there is nothing to measure. */
            Integer reachPercent) implements Serializable {
    }

    public record PromotionBoardDto(
            List<CampaignDto> campaigns,
            /** Les objectifs publies, tels que la colonne de droite les affiche. */
            List<SafetyOverviewDtos.SpiDto> objectives,
            /** La politique de Just Culture, telle que l'exploitant la publie. */
            String justCulturePolicy,
            int running,
            int planned,
            int closed,
            int awaitingAcknowledgement,
            OffsetDateTime computedAt) implements Serializable {
    }
}
