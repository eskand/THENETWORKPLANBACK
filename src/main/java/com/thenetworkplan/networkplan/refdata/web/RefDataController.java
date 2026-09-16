package com.thenetworkplan.networkplan.refdata.web;

import com.thenetworkplan.networkplan.refdata.dto.AircraftTypeDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AircraftTypeService;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API17 / API18 — reference data read by dispatch. */
@RestController
@RequestMapping("/v1/refdata")
public class RefDataController {

    private final AirportService airportService;
    private final AircraftTypeService aircraftTypeService;

    public RefDataController(AirportService airportService, AircraftTypeService aircraftTypeService) {
        this.airportService = airportService;
        this.aircraftTypeService = aircraftTypeService;
    }

    @GetMapping("/airports/{icao}")
    public AirportDto airport(@PathVariable String icao) {
        return airportService.findByIcao(icao.toUpperCase());
    }

    @GetMapping("/aircraft-types/{icaoType}")
    public AircraftTypeDto aircraftType(@PathVariable String icaoType) {
        return aircraftTypeService.findByIcaoType(icaoType.toUpperCase());
    }
}
