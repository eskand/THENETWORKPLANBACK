package com.thenetworkplan.networkplan.camoadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Adding or amending a programme task.
 *
 * <p>At least one interval is required for a mandatory task — the service
 * enforces it, because it is the same rule the database check enforces, and
 * because a mandatory task with no limit can never be shown as in date.
 */
public record SaveProgrammeTaskCommand(
        @NotBlank String icaoType,
        @NotBlank String code,
        @NotBlank String title,
        String ataChapter,
        BigDecimal intervalHours,
        Integer intervalCycles,
        Integer intervalMonths,
        BigDecimal toleranceHours,
        Integer toleranceDays,
        Boolean mandatory,
        String reference) {
}
