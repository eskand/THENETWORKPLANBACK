package com.thenetworkplan.networkplan.weather.service;

import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.RecordMetarCommand;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.WeatherBoardDto;
import java.util.List;
import java.util.UUID;

/** Station weather: what has been received, and how old it is. */
public interface WeatherService {

    /**
     * The stations asked for, each with its latest observation.
     *
     * <p>Refetches from the source only what is older than the refresh
     * window; everything else is served from the rows already stored.
     */
    WeatherBoardDto findStations(UUID tenantId, List<String> icaoCodes);

    /** Records a message read over the radio or copied from an ATIS. */
    ObservationDto record(UUID tenantId, RecordMetarCommand command, UUID actorId);
}
