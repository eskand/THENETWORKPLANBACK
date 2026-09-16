package com.thenetworkplan.networkplan.techlog.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The tech log board: the defect picture of the whole fleet.
 *
 * <p>Every figure here is counted at read time from the defects themselves.
 * None is stored, because a stored count is a count that can be wrong while
 * the rows beneath it are right — and this is the screen an engineer uses to
 * decide whether an aircraft flies.
 */
public record TechLogBoardDto(
        int open,
        /** How many registrations carry at least one open defect. */
        int openAircraft,
        int deferred,
        /** The registrations carrying a deferral, named. A count alone is not actionable. */
        List<String> deferredRegistrations,
        /** Closed in the last seven days — the rate of rectification, not the backlog. */
        int closedLastSevenDays,
        int aircraftAffected,
        int fleetSize,
        /**
         * The same chapter, on the same aircraft, twice inside thirty days.
         *
         * <p>A repeat defect is the one an engineer must not be allowed to miss:
         * it means the first rectification did not hold.
         */
        int repeatDefects,
        /** Null while nothing has been closed — not zero, which would read as instant. */
        Double averageDaysToClose,
        List<DefectRowDto> defects,
        OffsetDateTime computedAt) implements Serializable {

    /**
     * One defect, with everything the board and its detail panel show.
     *
     * <p>Wider than {@link DefectDto} on purpose: the panel shows the MEL
     * deferral behind a deferred defect, and fetching that per row would be a
     * request per line on a list read under time pressure.
     */
    public record DefectRowDto(
            UUID id,
            UUID aircraftId,
            String registration,
            String aircraftType,
            String icaoType,
            String ataChapter,
            /** Read from the ATA chapter table, never stored on the defect. */
            String system,
            String description,
            OffsetDateTime reportedAt,
            String reportedByName,
            /** The page the defect was raised on, when it was raised in flight. */
            String flightRef,
            String status,
            UUID melItemId,
            String melReference,
            String melCategory,
            String melLimit,
            OffsetDateTime melDueAt,
            Long melDaysRemaining,
            String correctiveAction,
            String closedByName,
            OffsetDateTime closedAt) implements Serializable {
    }
}
