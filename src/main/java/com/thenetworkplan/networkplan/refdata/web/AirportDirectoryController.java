package com.thenetworkplan.networkplan.refdata.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.refdata.dto.AirportDetailDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportDirectoryDto;
import com.thenetworkplan.networkplan.refdata.service.AirportDirectoryService;
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

    /**
     * The directory, always a page.
     *
     * <p>{@code limit} defaults to two hundred and is clamped server-side: the
     * table holds nine and a half thousand aerodromes, and no caller — ours or
     * anyone's — should be able to ask for all of them in one request.
     */
    @GetMapping
    public AirportDirectoryDto search(@RequestParam(name = "search", required = false) String search,
                                      @RequestParam(name = "country", required = false) String country,
                                      @RequestParam(name = "region", required = false) Short region,
                                      @RequestParam(name = "usedOnly", defaultValue = "false") boolean usedOnly,
                                      @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return airportDirectoryService.search(TenantContext.require(), search, country,
                region, usedOnly, limit);
    }

    @GetMapping("/{icao}")
    public AirportDetailDto detail(@PathVariable String icao) {
        return airportDirectoryService.findDetail(TenantContext.require(), icao);
    }
}
