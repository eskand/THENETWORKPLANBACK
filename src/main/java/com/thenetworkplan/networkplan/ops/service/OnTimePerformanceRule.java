package com.thenetworkplan.networkplan.ops.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * On-time performance, measured once for the whole product.
 *
 * <p>It lives here, alone, because the audited prototype computed it in three
 * places with two definitions — the OCC tile and the Timeline tile counted
 * every leg of the day, including those that had not left yet, so a morning
 * with nothing departed read as 100 %. Two definitions of "on time" in one
 * product become two answers the day someone compares them.
 *
 * <p>The definition held here: a leg counts only once it has actually gone
 * ({@code outAt} set), and it is on time when it went no later than the
 * operator's tolerance after its scheduled departure. A window with no
 * departure has no percentage — {@link Measure#percent()} is null, and the
 * screen says "no departure recorded yet" rather than showing a hundred.
 */
public interface OnTimePerformanceRule {

    /**
     * @param percent null when nothing has departed — never zero, never a hundred
     * @param sample  how many departures the figure rests on
     * @param onTime  how many of those were within tolerance
     */
    record Measure(Integer percent, int sample, int onTime) {

        public static final Measure NOTHING_DEPARTED = new Measure(null, 0, 0);
    }

    /** One leg, reduced to what the rule needs. Keeps the rule free of any entity. */
    record Departure(OffsetDateTime scheduled, OffsetDateTime actual) {
    }

    Measure measure(List<Departure> departures);
}
