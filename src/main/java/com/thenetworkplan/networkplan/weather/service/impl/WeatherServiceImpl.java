package com.thenetworkplan.networkplan.weather.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.config.WeatherProperties;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.weather.domain.WeatherObservation;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.LvpDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.RecordMetarCommand;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.StationWeatherDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.WeatherBoardDto;
import com.thenetworkplan.networkplan.weather.repository.WeatherObservationRepository;
import com.thenetworkplan.networkplan.weather.service.LowVisibilityRule;
import com.thenetworkplan.networkplan.weather.service.MetarParser;
import com.thenetworkplan.networkplan.weather.service.MetarSource;
import com.thenetworkplan.networkplan.weather.service.WeatherService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Station weather.
 *
 * <p>Three rules, and they are the whole module. A station with no row shows
 * {@code NO_OBSERVATION} — never the last thing we happen to have. An
 * observation older than the operator threshold shows {@code STALE} with its
 * age, because a ninety-minute-old METAR is not the weather. And the raw
 * message is stored next to every decoded field, so a decoder bug can always
 * be caught by reading the text.
 */
@Service
@Transactional(readOnly = true)
public class WeatherServiceImpl implements WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherServiceImpl.class);

    private final WeatherObservationRepository repository;
    private final MetarParser parser;
    private final List<MetarSource> sources;
    private final AirportService airportService;
    private final WeatherProperties properties;
    private final LowVisibilityRule lowVisibilityRule;

    public WeatherServiceImpl(WeatherObservationRepository repository,
                              MetarParser parser,
                              List<MetarSource> sources,
                              AirportService airportService,
                              WeatherProperties properties,
                              LowVisibilityRule lowVisibilityRule) {
        this.repository = repository;
        this.parser = parser;
        this.sources = sources;
        this.airportService = airportService;
        this.properties = properties;
        this.lowVisibilityRule = lowVisibilityRule;
    }

    @Override
    @Transactional
    public WeatherBoardDto findStations(UUID tenantId, List<String> icaoCodes) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Set<String> stations = new LinkedHashSet<>();
        icaoCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .forEach(stations::add);

        if (stations.isEmpty()) {
            return new WeatherBoardDto(List.of(), properties.getProvider(),
                    activeSource() != null, staleMinutes(), lowVisibility(List.of()), now);
        }

        Map<String, WeatherObservation> latest = latestByStation(stations, now);

        // Only the stations whose newest row is older than the refresh window
        // are asked for again: a board refreshed every thirty seconds does not
        // hit a public service every thirty seconds.
        Set<String> toRefresh = new LinkedHashSet<>();
        for (String station : stations) {
            WeatherObservation observation = latest.get(station);
            if (observation == null
                    || Duration.between(observation.getReceivedAt(), now)
                               .compareTo(properties.getRefreshAfter()) > 0) {
                toRefresh.add(station);
            }
        }

        MetarSource source = activeSource();
        if (source != null && !toRefresh.isEmpty()) {
            for (Map.Entry<String, String> answer : source.fetch(toRefresh).entrySet()) {
                WeatherObservation stored = store(answer.getValue(), source.provider(), now, null);
                if (stored != null) {
                    latest.put(stored.getStationIcao(), stored);
                }
            }
        }

        Map<String, AirportDto> airports = airportService.findAllByIcao(stations);

        List<StationWeatherDto> rows = new ArrayList<>(stations.size());
        for (String station : stations) {
            WeatherObservation observation = latest.get(station);
            AirportDto airport = airports.get(station);
            String name = airport == null ? station : airport.name();

            if (observation == null) {
                rows.add(new StationWeatherDto(station, name, null, "NO_OBSERVATION"));
                continue;
            }
            long age = Duration.between(observation.getObservedAt(), now).toMinutes();
            String state = age > properties.getStaleAfter().toMinutes() ? "STALE" : "LIVE";
            rows.add(new StationWeatherDto(station, name, toDto(observation, age), state));
        }

        return new WeatherBoardDto(rows, properties.getProvider(), source != null,
                staleMinutes(), lowVisibility(rows), now);
    }

    @Override
    @Transactional
    public ObservationDto record(UUID tenantId, RecordMetarCommand command, UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        WeatherObservation stored = store(command.rawText(), "MANUAL", now, actorId);
        if (stored == null) {
            throw new BusinessRuleException("METAR_NOT_READABLE",
                    "This text could not be read as a METAR: a station identifier and a "
                            + "ddhhmmZ time group are the minimum");
        }
        return toDto(stored, Duration.between(stored.getObservedAt(), now).toMinutes());
    }

    // ----------------------------------------------------------------

    /** Newest row per station, from one query over a bounded window. */
    private Map<String, WeatherObservation> latestByStation(Set<String> stations, OffsetDateTime now) {
        Map<String, WeatherObservation> latest = new HashMap<>();
        for (WeatherObservation observation : repository.findRecent(stations, now.minusHours(12))) {
            latest.putIfAbsent(observation.getStationIcao(), observation);
        }
        return latest;
    }

    /**
     * Decodes and stores one message.
     *
     * <p>Idempotent on (station, type, observed at): the same METAR fetched
     * twice is stored once, so refreshing a screen does not fill the table.
     */
    private WeatherObservation store(String raw, String provider, OffsetDateTime now, UUID actorId) {
        MetarParser.Decoded decoded = parser.parse(raw, now);
        if (decoded == null) {
            log.debug("Unreadable message from {}: {}", provider, raw);
            return null;
        }

        WeatherObservation existing = repository
                .findByStationIcaoAndReportTypeAndObservedAt(
                        decoded.stationIcao(), decoded.reportType(), decoded.observedAt())
                .orElse(null);
        if (existing != null) {
            // Already known. Touch the reception time so the refresh window
            // restarts: we did ask, and the answer was the same message.
            existing.setReceivedAt(now);
            return repository.save(existing);
        }

        WeatherObservation observation = new WeatherObservation();
        observation.setStationIcao(decoded.stationIcao());
        observation.setReportType(decoded.reportType());
        observation.setObservedAt(decoded.observedAt());
        observation.setReceivedAt(now);
        observation.setRawText(decoded.rawText());
        observation.setProvider(provider);
        observation.setWindDirDeg(decoded.windDirDeg());
        observation.setWindVariable(decoded.windVariable());
        observation.setWindSpeedKt(decoded.windSpeedKt());
        observation.setWindGustKt(decoded.windGustKt());
        observation.setVisibilityM(decoded.visibilityM());
        observation.setCavok(decoded.cavok());
        observation.setCeilingFt(decoded.ceilingFt());
        observation.setTemperatureC(decoded.temperatureC());
        observation.setDewpointC(decoded.dewpointC());
        observation.setQnhHpa(decoded.qnhHpa());
        observation.setConditions(decoded.conditions());
        observation.setFlightCategory(decoded.flightCategory());
        observation.getSource().setType("MANUAL".equals(provider) ? "manual" : "integration");
        observation.getSource().setReference(provider);
        observation.getSource().setAuthor(actorId);
        return repository.save(observation);
    }

    /**
     * The low visibility verdict across the stations the board was asked for.
     *
     * <p>Only stations shown as LIVE are assessed. A stale message says what
     * the weather was ninety minutes ago, and a procedure is not armed on
     * that. A station whose message carries neither visibility nor ceiling is
     * counted as not assessable, never as clear — that distinction is the
     * whole point of the tile.
     */
    private LvpDto lowVisibility(List<StationWeatherDto> rows) {
        int minimaVis = properties.getLvpVisibilityM();
        int minimaCeiling = properties.getLvpCeilingFt();

        List<String> below = new ArrayList<>();
        int assessed = 0;
        int notAssessable = 0;

        for (StationWeatherDto row : rows) {
            if (!"LIVE".equals(row.state())) {
                continue;
            }
            switch (lowVisibilityRule.assess(row.observation(), minimaVis, minimaCeiling)) {
                case BELOW -> {
                    assessed++;
                    below.add(row.icao());
                }
                case ABOVE -> assessed++;
                case NOT_ASSESSABLE -> notAssessable++;
            }
        }

        String state = assessed == 0 ? "NO_OBSERVATION" : below.isEmpty() ? "CLEAR" : "ACTIVE";
        return new LvpDto(state, List.copyOf(below), assessed, notAssessable, minimaVis, minimaCeiling);
    }

    private MetarSource activeSource() {
        return sources.stream().filter(MetarSource::isEnabled).findFirst().orElse(null);
    }

    private int staleMinutes() {
        return (int) properties.getStaleAfter().toMinutes();
    }

    private ObservationDto toDto(WeatherObservation observation, long ageMinutes) {
        return new ObservationDto(
                observation.getReportType(),
                observation.getObservedAt(),
                observation.getReceivedAt(),
                ageMinutes,
                observation.getRawText(),
                observation.getProvider(),
                observation.getWindDirDeg(),
                observation.isWindVariable(),
                observation.getWindSpeedKt(),
                observation.getWindGustKt(),
                observation.getVisibilityM(),
                observation.isCavok(),
                observation.getCeilingFt(),
                observation.getTemperatureC(),
                observation.getDewpointC(),
                observation.getQnhHpa(),
                observation.getConditions(),
                observation.getFlightCategory());
    }
}
