package com.thenetworkplan.networkplan.timeline.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One block on the timeline: a flight, or the ground time before it.
 *
 * <p>Ground time is a segment in its own right rather than a gap the browser
 * infers, because it is the thing an OCC actually looks at — and because a
 * turnaround shorter than the operator minimum has to be marked, not guessed
 * at from two adjacent bars.
 *
 * @param statusTone  the five states the lane is coloured by — SCHEDULED,
 *                    ENROUTE, DELAYED, AOG, MAINTENANCE. Derived here rather
 *                    than in the browser so the Timeline and the Dispatch
 *                    board cannot disagree on what "delayed" means
 * @param delayMinutes minutes past the schedule, zero when on time
 * @param melReference the open MEL item carried on this leg, or null. Read
 *                     from the same source as the dispatch board
 *                     ({@code findOpenMelByAircraft}), so a chip here and a
 *                     chip there always mean the same item
 * @param ftlStatus    worst FTL verdict among the crew of this leg — OK,
 *                     WARNING, BREACH, or UNKNOWN when no one is assigned
 */
public record TimelineSegmentDto(
        String kind,
        UUID legId,
        String flightNo,
        String depIcao,
        String arrIcao,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        long minutes,
        String status,
        String statusTone,
        int delayMinutes,
        String melReference,
        boolean melBlocking,
        String ftlStatus,
        /** Ground segments only: true when shorter than the operator minimum. */
        boolean tight,
        String note) implements Serializable {

    public static final String FLIGHT = "FLIGHT";
    public static final String GROUND = "GROUND";
    public static final String MAINTENANCE = "MAINTENANCE";
}
