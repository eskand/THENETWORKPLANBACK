package com.thenetworkplan.networkplan.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The commands of Sales & CRM. */
public final class SalesCommands {

    private SalesCommands() {
    }

    public record SaveRequestCommand(
            @NotNull UUID clientId,
            @NotBlank @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code") String depIcao,
            @NotBlank @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code") String arrIcao,
            @NotNull OffsetDateTime departureAt,
            OffsetDateTime returnAt,
            @Min(0) int paxCount,
            String flightType,
            String icaoType,
            String remark) {
    }

    /**
     * Recording the feasibility study.
     *
     * <p>A note is required for anything other than {@code FEASIBLE}: a refusal
     * or a condition that carries no reason cannot be acted on by the seller.
     */
    public record AssessFeasibilityCommand(
            @NotBlank String feasibility,
            String note) {
    }

    public record CreateQuoteCommand(
            @NotBlank String currency,
            LocalDate validUntil,
            String remark) {
    }

    /**
     * Adding a line.
     *
     * <p>{@code fxRate} is mandatory when the line is not in the quote
     * currency: the calculator refuses to guess, and a rate of one on a
     * foreign line is reported as a warning rather than summed silently.
     */
    public record AddQuoteLineCommand(
            @NotBlank String kind,
            @NotBlank String label,
            @NotNull BigDecimal quantity,
            String unit,
            @NotNull BigDecimal unitPrice,
            @NotBlank String currency,
            BigDecimal fxRate,
            LocalDate fxRateAt,
            Boolean taxable) {
    }

    /** Sending, accepting or refusing a quote. */
    public record DecideQuoteCommand(
            @NotBlank String status,
            String remark) {
    }
}
