package com.thenetworkplan.networkplan.tripsupport.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.tripsupport.dto.SupplierDto;
import com.thenetworkplan.networkplan.tripsupport.dto.TripSupportBoardDto;
import com.thenetworkplan.networkplan.tripsupport.service.TripSupportBoardService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API42 — NetPlus Services.
 *
 * <p>Reading only. The writes are the ones DOM2 already exposes on
 * {@code TripSupportController}: {@code POST /v1/legs/{id}/service-requests},
 * {@code POST /v1/legs/{id}/permit-requests} and the two status patches. One
 * write path per fact, whichever screen calls it.
 */
@RestController
@RequestMapping("/v1/netplus-services")
public class NetPlusServicesController {

    private final TripSupportBoardService boardService;

    public NetPlusServicesController(TripSupportBoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/board")
    public TripSupportBoardDto board(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return boardService.findBoard(TenantContext.require(), day);
    }

    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers(@RequestParam(name = "station", required = false) String station) {
        return boardService.findSuppliers(TenantContext.require(), station);
    }
}
