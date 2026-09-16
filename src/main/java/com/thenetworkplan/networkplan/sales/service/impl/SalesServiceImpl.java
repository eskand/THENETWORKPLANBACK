package com.thenetworkplan.networkplan.sales.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.refdata.repository.AircraftTypeRepository;
import com.thenetworkplan.networkplan.sales.domain.Client;
import com.thenetworkplan.networkplan.sales.domain.Feasibility;
import com.thenetworkplan.networkplan.sales.domain.Quote;
import com.thenetworkplan.networkplan.sales.domain.QuoteLine;
import com.thenetworkplan.networkplan.sales.domain.QuoteLineKind;
import com.thenetworkplan.networkplan.sales.domain.QuoteStatus;
import com.thenetworkplan.networkplan.sales.domain.SalesRequest;
import com.thenetworkplan.networkplan.sales.domain.SalesRequestStatus;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AddQuoteLineCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AssessFeasibilityCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.CreateQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.DecideQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.SaveRequestCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.ClientDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.QuoteDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesBoardDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesRequestDto;
import com.thenetworkplan.networkplan.sales.mapper.SalesMapper;
import com.thenetworkplan.networkplan.sales.repository.ClientRepository;
import com.thenetworkplan.networkplan.sales.repository.QuoteLineRepository;
import com.thenetworkplan.networkplan.sales.repository.QuoteRepository;
import com.thenetworkplan.networkplan.sales.repository.SalesRequestRepository;
import com.thenetworkplan.networkplan.sales.service.PricingCalculator;
import com.thenetworkplan.networkplan.sales.service.SalesService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales & CRM.
 *
 * <p>Three statements answer the board: the requests, their quotes, the lines
 * of those quotes. The totals are then computed by {@code PricingCalculator},
 * once per quote, in memory.
 *
 * <p>The pipeline figure is deliberately a sum of <em>converted</em> amounts in
 * one declared currency, and the response says which. Adding a dollar quote to
 * a euro quote and printing the result was the audit's finding.
 */
@Service
@Transactional(readOnly = true)
public class SalesServiceImpl implements SalesService {

    private static final DateTimeFormatter REF_DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String PIPELINE_CURRENCY = "EUR";

    private final SalesRequestRepository requestRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteLineRepository lineRepository;
    private final ClientRepository clientRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final PricingCalculator pricingCalculator;
    private final SalesMapper mapper;

    public SalesServiceImpl(SalesRequestRepository requestRepository,
                            QuoteRepository quoteRepository,
                            QuoteLineRepository lineRepository,
                            ClientRepository clientRepository,
                            AircraftTypeRepository aircraftTypeRepository,
                            PricingCalculator pricingCalculator,
                            SalesMapper mapper) {
        this.requestRepository = requestRepository;
        this.quoteRepository = quoteRepository;
        this.lineRepository = lineRepository;
        this.clientRepository = clientRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.pricingCalculator = pricingCalculator;
        this.mapper = mapper;
    }

    @Override
    public SalesBoardDto findBoard(UUID tenantId, String status) {
        SalesRequestStatus filter = (status == null || status.isBlank())
                ? null
                : parseEnum(SalesRequestStatus.class, status, "SALES_STATUS_UNKNOWN");

        List<SalesRequest> requests = requestRepository.findAll(tenantId, filter);
        if (requests.isEmpty()) {
            return new SalesBoardDto(List.of(), 0, 0, 0, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, PIPELINE_CURRENCY,
                    OffsetDateTime.now(ZoneOffset.UTC));
        }

        Map<UUID, List<QuoteDto>> quotesByRequest = quotesFor(tenantId,
                requests.stream().map(SalesRequest::getId).toList());

        List<SalesRequestDto> rows = requests.stream()
                .map(request -> mapper.toDto(request,
                        quotesByRequest.getOrDefault(request.getId(), List.of())))
                .toList();

        BigDecimal pipeline = BigDecimal.ZERO;
        BigDecimal won = BigDecimal.ZERO;
        int fresh = 0;
        int quoted = 0;
        int wonCount = 0;
        int lost = 0;
        int unknownFeasibility = 0;

        for (SalesRequestDto row : rows) {
            switch (row.status()) {
                case "NEW" -> fresh++;
                case "QUOTED" -> quoted++;
                case "WON" -> wonCount++;
                case "LOST" -> lost++;
                default -> { }
            }
            if ("UNKNOWN".equals(row.feasibility())) {
                unknownFeasibility++;
            }
            if (row.bestQuoteTotal() != null && PIPELINE_CURRENCY.equals(row.bestQuoteCurrency())) {
                if ("WON".equals(row.status())) {
                    won = won.add(row.bestQuoteTotal());
                } else if ("QUOTED".equals(row.status())) {
                    pipeline = pipeline.add(row.bestQuoteTotal());
                }
            }
        }

        return new SalesBoardDto(rows, rows.size(), fresh, quoted, wonCount, lost,
                unknownFeasibility, pipeline, won, PIPELINE_CURRENCY,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public SalesRequestDto findRequest(UUID tenantId, UUID requestId) {
        SalesRequest request = require(tenantId, requestId);
        return mapper.toDto(request,
                quotesFor(tenantId, List.of(requestId)).getOrDefault(requestId, List.of()));
    }

    @Override
    public List<ClientDto> findClients(UUID tenantId) {
        return clientRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public SalesRequestDto createRequest(UUID tenantId, SaveRequestCommand command) {
        Client client = clientRepository.findByTenantIdAndId(tenantId, command.clientId())
                .orElseThrow(() -> ResourceNotFoundException.of("Client", command.clientId()));

        SalesRequest request = new SalesRequest();
        request.setTenantId(tenantId);
        request.setClient(client);
        request.setReference(nextReference(tenantId));
        request.setDepIcao(command.depIcao());
        request.setArrIcao(command.arrIcao());
        request.setDepartureAt(command.departureAt());
        request.setReturnAt(command.returnAt());
        request.setPaxCount(command.paxCount());
        request.setFlightType(command.flightType() == null ? "PAX" : command.flightType());
        request.setRemark(command.remark());
        // Feasibility starts UNKNOWN, on purpose: nobody has looked yet.
        request.setFeasibility(Feasibility.UNKNOWN);
        request.setStatus(SalesRequestStatus.NEW);

        if (command.icaoType() != null && !command.icaoType().isBlank()) {
            request.setAircraftType(aircraftTypeRepository
                    .findByIcaoType(command.icaoType().trim().toUpperCase())
                    .orElseThrow(() -> ResourceNotFoundException.of("Aircraft type", command.icaoType())));
        }
        return mapper.toDto(requestRepository.save(request), List.of());
    }

    @Override
    @Transactional
    public SalesRequestDto assessFeasibility(UUID tenantId, UUID requestId, AssessFeasibilityCommand command) {
        SalesRequest request = require(tenantId, requestId);
        Feasibility verdict = parseEnum(Feasibility.class, command.feasibility(), "FEASIBILITY_UNKNOWN_VALUE");

        if (verdict != Feasibility.FEASIBLE && (command.note() == null || command.note().isBlank())) {
            throw new BusinessRuleException("FEASIBILITY_NOTE_REQUIRED",
                    "A refusal or a condition has to say why; the seller cannot act on a verdict alone");
        }
        request.setFeasibility(verdict);
        request.setFeasibilityNote(command.note());
        return mapper.toDto(requestRepository.save(request),
                quotesFor(tenantId, List.of(requestId)).getOrDefault(requestId, List.of()));
    }

    @Override
    @Transactional
    public QuoteDto createQuote(UUID tenantId, UUID requestId, CreateQuoteCommand command) {
        SalesRequest request = require(tenantId, requestId);
        if (!request.getFeasibility().allowsQuoting()) {
            throw new BusinessRuleException("REQUEST_NOT_FEASIBLE",
                    "Feasibility is " + request.getFeasibility()
                            + ": assess it before quoting, or the operator quotes a trip it cannot fly");
        }

        String reference = request.getReference().replace("RFQ", "QUO");
        Quote quote = new Quote();
        quote.setTenantId(tenantId);
        quote.setRequest(request);
        quote.setReference(reference);
        quote.setVersion(quoteRepository.currentVersion(tenantId, reference) + 1);
        quote.setCurrency(command.currency().trim().toUpperCase());
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setValidUntil(command.validUntil());
        quote.setRemark(command.remark());
        Quote saved = quoteRepository.save(quote);

        return mapper.toDto(saved, List.of(), pricingCalculator.total(saved.getCurrency(), List.of()));
    }

    @Override
    @Transactional
    public QuoteDto addLine(UUID tenantId, UUID quoteId, AddQuoteLineCommand command) {
        Quote quote = quoteRepository.findOne(tenantId, quoteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Quote", quoteId));
        if (!quote.getStatus().isEditable()) {
            throw new BusinessRuleException("QUOTE_NOT_EDITABLE",
                    "Quote " + quote.getReference() + " is " + quote.getStatus()
                            + "; issue a new version instead of changing what the client received");
        }

        String currency = command.currency().trim().toUpperCase();
        BigDecimal rate = command.fxRate();
        if (currency.equals(quote.getCurrency())) {
            rate = BigDecimal.ONE;
        } else if (rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException("QUOTE_LINE_FX_REQUIRED",
                    "A line in " + currency + " on a quote in " + quote.getCurrency()
                            + " needs an exchange rate; the total cannot be a sum of two currencies");
        }

        QuoteLine line = new QuoteLine();
        line.setTenantId(tenantId);
        line.setQuote(quote);
        line.setLineNo(lineRepository.lastLineNo(quoteId) + 1);
        line.setKind(parseEnum(QuoteLineKind.class, command.kind(), "QUOTE_LINE_KIND_UNKNOWN"));
        line.setLabel(command.label());
        line.setQuantity(command.quantity());
        line.setUnit(command.unit());
        line.setUnitPrice(command.unitPrice());
        line.setCurrency(currency);
        line.setFxRate(rate);
        line.setFxRateAt(command.fxRateAt());
        line.setTaxable(command.taxable() == null || command.taxable());
        lineRepository.save(line);

        List<QuoteLine> lines = lineRepository.findByQuoteIds(tenantId, List.of(quoteId));
        return mapper.toDto(quote, lines, pricingCalculator.total(quote.getCurrency(), lines));
    }

    @Override
    @Transactional
    public QuoteDto decide(UUID tenantId, UUID quoteId, DecideQuoteCommand command, UUID actorId) {
        Quote quote = quoteRepository.findOne(tenantId, quoteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Quote", quoteId));
        QuoteStatus target = parseEnum(QuoteStatus.class, command.status(), "QUOTE_STATUS_UNKNOWN");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<QuoteLine> lines = lineRepository.findByQuoteIds(tenantId, List.of(quoteId));
        if (target == QuoteStatus.SENT) {
            if (lines.isEmpty()) {
                throw new BusinessRuleException("QUOTE_EMPTY", "An empty quote cannot be sent");
            }
            PricingCalculator.Totals totals = pricingCalculator.total(quote.getCurrency(), lines);
            if (!totals.warnings().isEmpty()) {
                // Sending a total the calculator does not trust is exactly the
                // defect the audit found; the warnings are named in the refusal.
                throw new BusinessRuleException("QUOTE_FX_UNRELIABLE",
                        "The total cannot be trusted: " + String.join("; ", totals.warnings()));
            }
            quote.setSentAt(now);
        }
        if (target.isDecided()) {
            quote.setDecidedAt(now);
            quote.setDecidedBy(actorId);
            SalesRequest request = quote.getRequest();
            request.setStatus(target == QuoteStatus.ACCEPTED
                    ? SalesRequestStatus.WON
                    : SalesRequestStatus.LOST);
            requestRepository.save(request);
        }
        quote.setStatus(target);
        if (command.remark() != null) {
            quote.setRemark(command.remark());
        }
        Quote saved = quoteRepository.save(quote);

        return mapper.toDto(saved, lines, pricingCalculator.total(saved.getCurrency(), lines));
    }

    // ----------------------------------------------------------------

    /** Quotes and their lines for a set of requests: two statements, no loop. */
    private Map<UUID, List<QuoteDto>> quotesFor(UUID tenantId, List<UUID> requestIds) {
        List<Quote> quotes = quoteRepository.findByRequestIds(tenantId, requestIds);
        if (quotes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<QuoteLine>> linesByQuote = new HashMap<>();
        for (QuoteLine line : lineRepository.findByQuoteIds(tenantId,
                quotes.stream().map(Quote::getId).toList())) {
            linesByQuote.computeIfAbsent(line.getQuote().getId(), key -> new ArrayList<>()).add(line);
        }

        Map<UUID, List<QuoteDto>> byRequest = new HashMap<>();
        for (Quote quote : quotes) {
            List<QuoteLine> lines = linesByQuote.getOrDefault(quote.getId(), List.of());
            byRequest.computeIfAbsent(quote.getRequest().getId(), key -> new ArrayList<>())
                    .add(mapper.toDto(quote, lines,
                            pricingCalculator.total(quote.getCurrency(), lines)));
        }
        return byRequest;
    }

    private SalesRequest require(UUID tenantId, UUID requestId) {
        return requestRepository.findOne(tenantId, requestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Sales request", requestId));
    }

    private String nextReference(UUID tenantId) {
        String base = "RFQ-" + REF_DAY.format(OffsetDateTime.now(ZoneOffset.UTC));
        for (int suffix = 1; suffix < 100; suffix++) {
            String candidate = base + "-" + String.format("%02d", suffix);
            if (requestRepository.findByTenantIdAndReference(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleException("SALES_REFERENCE_EXHAUSTED",
                "More than ninety-nine requests today");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String rule) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(rule, "Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
