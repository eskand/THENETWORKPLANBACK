package com.thenetworkplan.networkplan.weather.service;

import java.time.OffsetDateTime;

/**
 * Decodes a METAR.
 *
 * <p>A rule with no repository and no I/O, like {@code CrewDocumentChecker}:
 * a decoder is exactly the kind of code that has to be testable on a hundred
 * real messages without a database in the way.
 *
 * <p>Two commitments. A group the message does not carry comes back null —
 * never zero, never a default. And a group the parser does not understand is
 * ignored rather than guessed at: the raw text is kept by the caller, so
 * nothing is lost.
 */
public interface MetarParser {

    /**
     * @param raw the message, with or without the NOAA date line before it
     * @param receivedAt when we learnt it, used to resolve the day-of-month
     *                   group into a full instant
     * @return the decoded message, or null when the text is not a METAR at all
     */
    Decoded parse(String raw, OffsetDateTime receivedAt);

    /**
     * @param stationIcao the station the message is for
     * @param observedAt  resolved from {@code ddhhmmZ} against {@code receivedAt}
     * @param conditions  present weather groups, joined, or null when none
     * @param ceilingFt   lowest BKN or OVC layer; null when the sky is clear
     *                    or not reported
     * @param flightCategory VFR / MVFR / IFR / LIFR, or null when neither
     *                    ceiling nor visibility is known
     */
    record Decoded(String stationIcao,
                   String reportType,
                   OffsetDateTime observedAt,
                   String rawText,
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
                   String flightCategory) {
    }
}
