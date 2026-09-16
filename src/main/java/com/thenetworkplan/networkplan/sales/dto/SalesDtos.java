package com.thenetworkplan.networkplan.sales.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The read models of Sales & CRM, kept together because they are read
 * together: a request, its quotes, their lines and the totals.
 *
 * <p>Grouping small records in one file is a deliberate exception to the
 * one-record-per-file habit of the other modules — these five are meaningless
 * apart, and splitting them would spread one screen over five files.
 */
public final class SalesDtos {

    private SalesDtos() {
    }

    public record ClientDto(
            UUID id,
            String code,
            String name,
            String kind,
            String countryIso2,
            String email,
            String phone,
            String paymentTerms,
            String currency,
            boolean active) implements Serializable {
    }

    public record QuoteLineDto(
            UUID id,
            int lineNo,
            String kind,
            String label,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            String currency,
            BigDecimal fxRate,
            LocalDate fxRateAt,
            /** quantity × unit price × rate, in the quote currency. */
            BigDecimal amount) implements Serializable {
    }

    public record QuoteDto(
            UUID id,
            UUID requestId,
            String reference,
            int version,
            String currency,
            String status,
            LocalDate validUntil,
            OffsetDateTime sentAt,
            OffsetDateTime decidedAt,
            UUID tripId,
            List<QuoteLineDto> lines,
            BigDecimal netTotal,
            BigDecimal deductions,
            Map<String, BigDecimal> totalByKind,
            List<String> currenciesUsed,
            /** Conversions the calculator could not trust, named one by one. */
            List<String> warnings,
            /** No tax rule is configured: the total is net and says so. */
            String taxNote) implements Serializable {
    }

    public record SalesRequestDto(
            UUID id,
            String reference,
            UUID clientId,
            String clientName,
            String clientKind,
            OffsetDateTime receivedAt,
            String depIcao,
            String arrIcao,
            OffsetDateTime departureAt,
            OffsetDateTime returnAt,
            int paxCount,
            String flightType,
            String icaoType,
            String status,
            String feasibility,
            String feasibilityNote,
            String remark,
            List<QuoteDto> quotes,
            BigDecimal bestQuoteTotal,
            String bestQuoteCurrency) implements Serializable {
    }

    public record SalesBoardDto(
            List<SalesRequestDto> requests,
            int total,
            int newRequests,
            int quoted,
            int won,
            int lost,
            int feasibilityUnknown,
            BigDecimal pipelineValue,
            BigDecimal wonValue,
            String currency,
            OffsetDateTime computedAt) implements Serializable {
    }
}
