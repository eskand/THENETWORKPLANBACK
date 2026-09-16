package com.thenetworkplan.networkplan.roster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Writing one cell of a draft grid. */
public record SaveRosterEntryCommand(
        @NotNull UUID personId,
        @NotNull LocalDate dutyDate,
        @NotBlank String code,
        String remark) {
}
