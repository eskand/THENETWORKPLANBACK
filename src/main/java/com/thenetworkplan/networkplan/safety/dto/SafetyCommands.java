package com.thenetworkplan.networkplan.safety.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The commands of the safety modules. */
public final class SafetyCommands {

    private SafetyCommands() {
    }

    /**
     * Filing a report.
     *
     * <p>{@code anonymous} is the reporter's choice and is honoured by the read
     * model. {@code reportedBy} may still be given with it: the row keeps who
     * filed, the screens do not show it.
     */
    public record ReportOccurrenceCommand(
            @NotNull OffsetDateTime occurredAt,
            @NotBlank String category,
            @NotBlank String title,
            @NotBlank String narrative,
            String phaseOfFlight,
            UUID reportedBy,
            Boolean anonymous,
            UUID legId,
            UUID aircraftId,
            String stationIcao,
            String eccairsEventType) {
    }

    /**
     * Assessing the risk.
     *
     * <p>Severity and probability only: the level comes from the operator
     * matrix in {@code safety.risk_matrix}, never from the caller. A caller
     * that could set the level could grade its own occurrence acceptable.
     */
    public record AssessRiskCommand(
            @NotBlank @Pattern(regexp = "^[A-E]$", message = "severity is A to E") String severity,
            @Min(1) @Max(5) int probability) {
    }

    public record CreateActionCommand(
            @NotBlank String title,
            String detail,
            UUID ownerUserId,
            LocalDate dueOn) {
    }

    public record UpdateActionCommand(
            @NotBlank String status,
            LocalDate completedOn,
            String effectiveness) {
    }

    /** Closing an occurrence. Refused while an action is still outstanding. */
    public record CloseOccurrenceCommand(
            @NotBlank String conclusion) {
    }

    /** Recording the ECCAIRS filing. The reference is what the authority returned. */
    public record FileWithAuthorityCommand(
            @NotBlank String eccairsReference,
            String occurrenceClass) {
    }

    public record AcknowledgeCampaignCommand(
            @NotNull UUID personId) {
    }
}
