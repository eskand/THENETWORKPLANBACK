package com.thenetworkplan.networkplan.crewscheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Putting one crew member in one seat of one leg.
 *
 * <p>No {@code force} flag: a blocking finding (absent, not rated, papers
 * expired, already flying) refuses the assignment outright. Rest below the
 * operator threshold is a warning and goes through — and is recorded as the
 * reason on the assignment.
 */
public record AssignSeatCommand(
        @NotNull UUID personId,
        @NotBlank String seat) {
}
