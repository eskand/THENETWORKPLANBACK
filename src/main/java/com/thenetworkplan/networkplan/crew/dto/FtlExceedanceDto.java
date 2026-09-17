package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One flight-time-limitation exceedance, with the clause it breached.
 *
 * <p><b>The rule travels with the finding.</b> « 11 hours of rest » means
 * nothing on its own; « 11 hours against the 12 required by ORO.FTL.235(a) »
 * is a finding somebody can act on and an inspector can check. A breach that
 * did not name its clause would have to be re-derived by every reader.
 *
 * @param severity High for an FDP or rest breach — those ground a crew member
 *                 on the day; Medium for a rolling-window ceiling, which is a
 *                 planning failure over weeks rather than a single duty.
 */
public record FtlExceedanceDto(
        LocalDate day,
        UUID personId,
        String crewName,
        String rule,
        String detail,
        long overMinutes,
        String severity) implements Serializable {
}
