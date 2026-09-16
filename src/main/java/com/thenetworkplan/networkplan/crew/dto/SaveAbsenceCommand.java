package com.thenetworkplan.networkplan.crew.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Declaring an absence. Both ends are inclusive. */
public record SaveAbsenceCommand(
        @NotNull String kind,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @Size(max = 240) String reason) {
}
