package com.thenetworkplan.networkplan.weather.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/** The read models of the weather panel. */
public final class WeatherDtos {

    private WeatherDtos() {
    }

    /**
     * One station.
     *
     * <p>{@code observation} is null when nothing has ever been received for
     * that station: the panel then says so, rather than showing the last
     * observation it happens to have.
     */
    public record StationWeatherDto(
            String icao,
            String name,
            ObservationDto observation,
            /** LIVE / STALE / NO_OBSERVATION */
            String state) implements Serializable {
    }

    public record ObservationDto(
            String reportType,
            OffsetDateTime observedAt,
            OffsetDateTime receivedAt,
            long ageMinutes,
            String rawText,
            String provider,
            Integer windDirDeg,
            boolean windVariable,
            Integer windSpeedKt,
            Integer windGustKt,
            Integer visibilityM,
            boolean cavok,
            Integer ceilingFt,
            Integer temperatureC,
            Integer dewpointC,
            Integer qnhHpa,
            String conditions,
            String flightCategory) implements Serializable {
    }

    public record WeatherBoardDto(
            List<StationWeatherDto> stations,
            String provider,
            boolean sourceConnected,
            int staleThresholdMinutes,
            /**
             * Low visibility procedures, assessed from the very observations
             * the panel shows. Computed here rather than in the browser for
             * the reason on-time performance is: two definitions of one state
             * in one product become two answers the day someone compares them.
             */
            LvpDto lvp,
            OffsetDateTime computedAt) implements Serializable {
    }

    /**
     * The low visibility picture across the bases.
     *
     * @param state         ACTIVE when at least one station is at or below the
     *                      minima, CLEAR when every assessed station is above
     *                      them, NO_OBSERVATION when nothing could be assessed.
     *                      There is no fourth value meaning "probably fine"
     * @param stations      the stations at or below the minima
     * @param assessed      how many stations the verdict rests on
     * @param notAssessable stations whose message says nothing about visibility
     *                      or ceiling. Counted, never read as clear
     */
    public record LvpDto(
            String state,
            List<String> stations,
            int assessed,
            int notAssessable,
            int visibilityMinimaM,
            int ceilingMinimaFt) implements Serializable {
    }

    /**
     * Recording a message read over the radio or copied from an ATIS.
     *
     * <p>Stored with {@code provider = MANUAL} for ever: a message typed by a
     * person is never presented as a receiver's.
     */
    public record RecordMetarCommand(@NotBlank String rawText) {
    }
}
