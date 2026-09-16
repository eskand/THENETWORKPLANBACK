package com.thenetworkplan.networkplan.sales.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AddQuoteLineCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.AssessFeasibilityCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.CreateQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.DecideQuoteCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesCommands.SaveRequestCommand;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.ClientDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.QuoteDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesBoardDto;
import com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesRequestDto;
import com.thenetworkplan.networkplan.sales.service.SalesService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API50 — Sales & CRM: requests, feasibility, quotes. */
@RestController
@RequestMapping("/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @GetMapping("/board")
    public SalesBoardDto board(@RequestParam(name = "status", required = false) String status) {
        return salesService.findBoard(TenantContext.require(), status);
    }

    @GetMapping("/requests/{id}")
    public SalesRequestDto request(@PathVariable UUID id) {
        return salesService.findRequest(TenantContext.require(), id);
    }

    @GetMapping("/clients")
    public List<ClientDto> clients() {
        return salesService.findClients(TenantContext.require());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesRequestDto createRequest(@Valid @RequestBody SaveRequestCommand command) {
        return salesService.createRequest(TenantContext.require(), command);
    }

    @PatchMapping("/requests/{id}/feasibility")
    public SalesRequestDto assess(@PathVariable UUID id,
                                  @Valid @RequestBody AssessFeasibilityCommand command) {
        return salesService.assessFeasibility(TenantContext.require(), id, command);
    }

    @PostMapping("/requests/{id}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteDto createQuote(@PathVariable UUID id, @Valid @RequestBody CreateQuoteCommand command) {
        return salesService.createQuote(TenantContext.require(), id, command);
    }

    @PostMapping("/quotes/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteDto addLine(@PathVariable UUID id, @Valid @RequestBody AddQuoteLineCommand command) {
        return salesService.addLine(TenantContext.require(), id, command);
    }

    @PatchMapping("/quotes/{id}")
    public QuoteDto decide(@PathVariable UUID id,
                           @Valid @RequestBody DecideQuoteCommand command,
                           @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return salesService.decide(TenantContext.require(), id, command, actorId);
    }
}
