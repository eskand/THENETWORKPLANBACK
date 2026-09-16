package com.thenetworkplan.networkplan.roster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Opening a new roster period, always as a draft. */
public record CreateRosterVersionCommand(
        @NotBlank String label,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        /** Copy the cells of this version into the new draft, when given. */
        java.util.UUID copyFromVersionId) {
}
