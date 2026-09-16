package com.thenetworkplan.networkplan.sales.service;

import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AddQuoteLineCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AssessFeasibilityCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.CreateQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.DecideQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.SaveRequestCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.ClientDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.QuoteDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesBoardDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesRequestDto;
import java.util.List;
import java.util.UUID;

/** DOM7 — Sales & CRM: clients, requests, quotes. */
public interface SalesService {

    SalesBoardDto findBoard(UUID tenantId, String status);

    SalesRequestDto findRequest(UUID tenantId, UUID requestId);

    List<ClientDto> findClients(UUID tenantId);

    SalesRequestDto createRequest(UUID tenantId, SaveRequestCommand command);

    SalesRequestDto assessFeasibility(UUID tenantId, UUID requestId, AssessFeasibilityCommand command);

    QuoteDto createQuote(UUID tenantId, UUID requestId, CreateQuoteCommand command);

    QuoteDto addLine(UUID tenantId, UUID quoteId, AddQuoteLineCommand command);

    /** Send, accept or refuse. Accepting is what moves the request to WON. */
    QuoteDto decide(UUID tenantId, UUID quoteId, DecideQuoteCommand command, UUID actorId);
}
