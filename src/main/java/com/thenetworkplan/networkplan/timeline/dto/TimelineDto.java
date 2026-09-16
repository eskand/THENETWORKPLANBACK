package com.thenetworkplan.networkplan.timeline.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The whole timeline in one call: the window, one row per registration, the
 * six header tiles, and the values the three selectors can take.
 *
 * <p>The selector options travel with the board rather than being inferred
 * from the rows the browser happens to hold: filter on "Base: LFML" and the
 * rows shrink, but the list of bases must not.
 *
 * @param fleetSize      registrations on the certificate
 * @param inService      serviceable ones
 * @param outOfService   AOG plus maintenance — the figure the AOG tile shows,
 *                       as in the prototype, whose tile counts both
 * @param outOfServiceRegistrations the tails behind that number
 * @param availabilityPercent {@code inService / fleetSize}
 * @param delays         legs running later than the operator tolerance
 * @param otpPercent     measured by {@code OnTimePerformanceRule} on departures
 *                       that actually happened; null when none has
 * @param otpSample      how many departures that figure rests on
 * @param blockMinutes   block time flown in the window
 * @param utilisationPercent block hours against
 *                       {@code fleetSize × dailyBlockHourReference × days}
 */
public record TimelineDto(
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        int days,
        List<TimelineRowDto> rows,
        int aircraftUsed,
        int aircraftIdle,
        int flights,
        int tightTurnarounds,
        int minimumTurnaroundMinutes,
        int fleetSize,
        int inService,
        int outOfService,
        List<String> outOfServiceRegistrations,
        int availabilityPercent,
        int delays,
        /**
         * The same window, one day earlier. Counted from the same table with
         * the same query, never estimated: a window with no programme the day
         * before returns zero and the tile drops the comparison rather than
         * announcing a trend it cannot measure.
         */
        int flightsYesterday,
        int delaysYesterday,
        Integer otpPercent,
        int otpSample,
        int otpTargetPercent,
        long blockMinutes,
        int utilisationPercent,
        int dailyBlockHourReference,
        List<String> fleetSections,
        List<String> bases,
        OffsetDateTime computedAt) implements Serializable {
}
