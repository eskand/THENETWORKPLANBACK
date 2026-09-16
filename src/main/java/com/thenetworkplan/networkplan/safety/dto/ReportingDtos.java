package com.thenetworkplan.networkplan.safety.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The read models of Safety Reports — the reporter's side of the system. */
public final class ReportingDtos {

    private ReportingDtos() {
    }

    /** One of the ten kinds of report anyone in the company can file. */
    public record ReportTypeDto(
            String key,
            String label,
            String shortLabel,
            String description,
            String category,
            /** True when anonymity would make the report impossible to act on. */
            boolean concernsTheReporter) implements Serializable {
    }

    public record DraftDto(
            UUID id,
            String reportType,
            String title,
            OffsetDateTime occurredAt,
            String phaseOfFlight,
            String stationIcao,
            String flightNo,
            String registration,
            String narrative,
            String immediateAction,
            String reporterSuggestion,
            boolean anonymous,
            boolean confidential,
            OffsetDateTime updatedAt,
            boolean submittable,
            UUID submittedOccurrenceId) implements Serializable {
    }

    /** One of the reporter's own reports, as they see it. */
    public record MyReportDto(
            UUID id,
            String reference,
            String reportType,
            String reportTypeLabel,
            String title,
            OffsetDateTime occurredAt,
            OffsetDateTime reportedAt,
            String status,
            String riskLevel,
            boolean anonymous,
            boolean confidential,
            int openActions,
            /** What the Safety Manager concluded, once the report is closed. */
            String outcome) implements Serializable {
    }

    /**
     * The Safety Reports screen.
     *
     * <p>{@code guidance} is the operator's own wording on how a report is
     * handled — the Just Culture policy. It is carried with the board rather
     * than hard-coded in the browser because it is the text the operator
     * publishes and may have to defend.
     */
    public record ReportingBoardDto(
            List<ReportTypeDto> types,
            List<DraftDto> drafts,
            List<MyReportDto> myReports,
            List<String> phases,
            List<String> registrations,
            String justCulturePolicy,
            int openActionsForMe) implements Serializable {
    }

    /** Saving a draft, or updating one. Nothing here is mandatory yet. */
    public record SaveDraftCommand(
            UUID draftId,
            String reportType,
            String title,
            OffsetDateTime occurredAt,
            String phaseOfFlight,
            String stationIcao,
            String flightNo,
            String registration,
            String narrative,
            String immediateAction,
            String reporterSuggestion,
            Boolean anonymous,
            Boolean confidential) {
    }

    /**
     * Sending the report.
     *
     * <p>Three fields are required and no more: the kind, one factual line and
     * what happened. Everything else helps the analysis, and none of it should
     * stand between somebody and filing — a hazard described in one line is
     * still worth having.
     */
    public record SubmitReportCommand(
            UUID draftId,
            @NotBlank String reportType,
            @NotBlank String title,
            @NotBlank String narrative,
            OffsetDateTime occurredAt,
            String phaseOfFlight,
            String stationIcao,
            String flightNo,
            String registration,
            String immediateAction,
            String reporterSuggestion,
            Boolean anonymous,
            Boolean confidential) {
    }
}
