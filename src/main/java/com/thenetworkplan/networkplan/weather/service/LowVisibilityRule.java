package com.thenetworkplan.networkplan.weather.service;

import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;

/**
 * Are low visibility procedures called for at this station?
 *
 * <p>A pure rule, like {@code MaintenanceDueRule} or {@code MelRectificationRule}:
 * it takes one decoded observation and the operator's minima, and answers.
 * It reads nothing, writes nothing and has no clock, so it can be tested with
 * a table of METARs.
 *
 * <p>Two points of honesty are built into the return type. A station whose
 * observation carries neither a visibility nor a ceiling is
 * {@link Verdict#NOT_ASSESSABLE} — not "clear": the audited prototype's habit
 * of reading a missing value as a good value is exactly what put "Weather feed
 * unavailable" next to a green tile. And CAVOK is a positive statement
 * (ceiling and visibility OK), so it is answered as {@link Verdict#ABOVE}
 * without needing a cloud group.
 */
public interface LowVisibilityRule {

    enum Verdict {
        /** Visibility or ceiling at or below the operator's LVP minima. */
        BELOW,
        /** Measured, and above the minima. */
        ABOVE,
        /** Nothing in the message says what the visibility or the ceiling is. */
        NOT_ASSESSABLE
    }

    /**
     * @param observation      the decoded message, or null when the station has none
     * @param visibilityMinimaM  visibility at or below which LVP apply, in metres
     * @param ceilingMinimaFt    ceiling at or below which LVP apply, in feet
     */
    Verdict assess(ObservationDto observation, int visibilityMinimaM, int ceilingMinimaFt);
}
