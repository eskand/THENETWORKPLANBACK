package com.thenetworkplan.networkplan.sales.mapper;

import com.thenetworkplan.networkplan.sales.domain.Client;
import com.thenetworkplan.networkplan.sales.domain.Quote;
import com.thenetworkplan.networkplan.sales.domain.QuoteLine;
import com.thenetworkplan.networkplan.sales.domain.SalesRequest;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.ClientDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.QuoteDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.QuoteLineDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesRequestDto;
import com.thenetworkplan.networkplan.sales.service.PricingCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SalesMapper {

    private static final String TAX_NOTE =
            "Net of tax: no tax rule is configured for this operator, so no tax is computed. "
                    + "A zero tax line would read as an exemption, which is not what is meant.";

    public ClientDto toDto(Client client) {
        return new ClientDto(
                client.getId(), client.getCode(), client.getName(), client.getKind().name(),
                client.getCountryIso2(), client.getEmail(), client.getPhone(),
                client.getPaymentTerms(), client.getCurrency(), client.isActive());
    }

    public QuoteLineDto toDto(QuoteLine line) {
        BigDecimal amount = line.getQuantity()
                .multiply(line.getUnitPrice())
                .multiply(line.getFxRate())
                .setScale(2, RoundingMode.HALF_UP);
        return new QuoteLineDto(
                line.getId(), line.getLineNo(), line.getKind().name(), line.getLabel(),
                line.getQuantity(), line.getUnit(), line.getUnitPrice(), line.getCurrency(),
                line.getFxRate(), line.getFxRateAt(),
                line.getKind().isDeduction() ? amount.abs().negate() : amount);
    }

    /** @param totals from {@code PricingCalculator}, computed once by the service */
    public QuoteDto toDto(Quote quote, List<QuoteLine> lines, PricingCalculator.Totals totals) {
        return new QuoteDto(
                quote.getId(),
                quote.getRequest().getId(),
                quote.getReference(),
                quote.getVersion(),
                quote.getCurrency(),
                quote.getStatus().name(),
                quote.getValidUntil(),
                quote.getSentAt(),
                quote.getDecidedAt(),
                quote.getTripId(),
                lines.stream().map(this::toDto).toList(),
                totals.net(),
                totals.deductions(),
                totals.byKind(),
                totals.currenciesUsed(),
                totals.warnings(),
                TAX_NOTE);
    }

    public SalesRequestDto toDto(SalesRequest request, List<QuoteDto> quotes) {
        QuoteDto best = quotes.stream()
                .filter(quote -> !"REFUSED".equals(quote.status()))
                .findFirst()
                .orElse(quotes.isEmpty() ? null : quotes.getFirst());

        return new SalesRequestDto(
                request.getId(),
                request.getReference(),
                request.getClient().getId(),
                request.getClient().getName(),
                request.getClient().getKind().name(),
                request.getReceivedAt(),
                request.getDepIcao(),
                request.getArrIcao(),
                request.getDepartureAt(),
                request.getReturnAt(),
                request.getPaxCount(),
                request.getFlightType(),
                request.getAircraftType() == null ? null : request.getAircraftType().getIcaoType(),
                request.getStatus().name(),
                request.getFeasibility().name(),
                request.getFeasibilityNote(),
                request.getRemark(),
                quotes,
                best == null ? null : best.netTotal(),
                best == null ? null : best.currency());
    }
}
