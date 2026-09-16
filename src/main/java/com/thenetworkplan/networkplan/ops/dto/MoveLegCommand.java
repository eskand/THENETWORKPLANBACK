package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** Rescheduling a leg. The reason is recorded on the leg's history. */
public record MoveLegCommand(
        @NotNull OffsetDateTime std,
        @NotNull OffsetDateTime sta,
        @NotBlank String reason,
        String delayCode) {
}
