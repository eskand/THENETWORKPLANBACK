package com.thenetworkplan.networkplan.weather.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.RecordMetarCommand;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.WeatherBoardDto;
import com.thenetworkplan.networkplan.weather.service.WeatherService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API44 — station weather.
 *
 * <p>One entry point for the whole product, as annexe A4 requires: no screen
 * calls a weather provider itself, and no provider token ever reaches a
 * browser.
 */
@RestController
@RequestMapping("/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** @param stations comma-separated ICAO codes, e.g. {@code DTTA,LFML,LFPB} */
    @GetMapping("/stations")
    public WeatherBoardDto stations(@RequestParam(name = "stations") List<String> stations) {
        return weatherService.findStations(TenantContext.require(), stations);
    }

    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationDto record(@Valid @RequestBody RecordMetarCommand command,
                                 @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return weatherService.record(TenantContext.require(), command, actorId);
    }
}
