package com.thenetworkplan.networkplan.camoadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registering a published directive.
 *
 * <p>Creating it also creates one application row per affected registration:
 * a directive that exists without saying which tails it hits is the kind of
 * half-recorded fact the audit found everywhere.
 */
public record SaveDirectiveCommand(
        @NotBlank String kind,
        @NotBlank String reference,
        @NotBlank String subject,
        String issuedBy,
        LocalDate issuedOn,
        LocalDate effectiveOn,
        String icaoType,
        LocalDate complianceByDate,
        BigDecimal complianceByHours,
        String method,
        Integer recurringMonths) {
}
