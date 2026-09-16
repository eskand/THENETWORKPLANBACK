package com.thenetworkplan.networkplan.crew.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Creating or amending a crew file.
 *
 * <p>The three expiry dates are optional and stay null when the operator does
 * not have them: a missing date must read as {@code UNKNOWN} downstream, and
 * inventing one to make the form valid is exactly what the audit found.
 */
public record SavePersonCommand(
        @NotBlank @Size(max = 16) String staffNo,
        @NotBlank @Size(max = 60) String firstName,
        @NotBlank @Size(max = 60) String lastName,
        @NotNull String mainRole,
        @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code") String baseIcao,
        LocalDate licenceExpiry,
        LocalDate medicalExpiry,
        LocalDate trainingExpiry,
        Boolean active) {
}
