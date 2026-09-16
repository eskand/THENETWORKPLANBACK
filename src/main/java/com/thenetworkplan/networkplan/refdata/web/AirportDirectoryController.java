package com.thenetworkplan.networkplan.refdata.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.refdata.dto.AirportDetailDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportRowDto;
import com.thenetworkplan.networkplan.refdata.service.AirportDirectoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API43 — Airports Data.
 *
 * <p>Distinct from {@code RefDataController}, which serves the cached
 * single-aerodrome read the dispatch board uses: this one is the directory
 * screen, and it composes four sources.
 */
@RestController
@RequestMapping("/v1/airports")
public class AirportDirectoryController {

    private final AirportDirectoryService airportDirectoryService;

    public AirportDirectoryController(AirportDirectoryService airportDirectoryService) {
        this.airportDirectoryService = airportDirectoryService;
    }

    @GetMapping
    public List<AirportRowDto> search(@RequestParam(name = "search", required = false) String search,
                                      @RequestParam(name = "country", required = false) String country,
                                      @RequestParam(name = "usedOnly", defaultValue = "false") boolean usedOnly) {
        return airportDirectoryService.search(TenantContext.require(), search, country, usedOnly);
    }

    @GetMapping("/{icao}")
    public AirportDetailDto detail(@PathVariable String icao) {
        return airportDirectoryService.findDetail(TenantContext.require(), icao);
    }
}
