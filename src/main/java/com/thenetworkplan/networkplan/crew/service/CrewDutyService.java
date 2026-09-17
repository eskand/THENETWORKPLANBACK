package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.crew.dto.CrewDutyDto;
import com.thenetworkplan.networkplan.crew.dto.FtlExceedanceDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Duty periods read across the whole crew.
 *
 * <p><b>Why the crew module owns this and not reporting.</b> The FTL sheet, the
 * roster report and the duty report all have to agree on what a duty period is
 * — whether standby counts, where the rest between two duties is measured from,
 * which end of a duty decides the day it belongs to. Three reports each reading
 * {@code crew.duty_periods} for themselves would be three answers, and the
 * first authority audit would find all three.
 */
public interface CrewDutyService {

    /**
     * Every duty reporting inside the window, ordered by person then report
     * time, with the rest before each one and the FDP ceiling that applied.
     *
     * <p>The window closes on the report time, not the off-duty time: a duty
     * that reports on the last day of the period and lands the next morning
     * belongs to the period it started in, which is how a crew member reads
     * their own line.
     */
    List<CrewDutyDto> findDuties(UUID tenantId, LocalDate from, LocalDate to);

    /**
     * Every flight-time-limitation exceedance in the window.
     *
     * <p><b>One engine, three readers.</b> The FTL sheet, the FTL violations
     * report and the Safety Manager's roster banner all ask this method. Three
     * copies of the arithmetic would be three answers, and the first authority
     * audit would find all three — which is the precise failure an SMS exists
     * to prevent.
     *
     * <p>Checks applied: the maximum daily FDP of ORO.FTL.205 Table 2, the
     * minimum rest of ORO.FTL.235(a), and the rolling 60 h / 7 days and
     * 190 h / 28 days ceilings of ORO.FTL.210(a). Rest and days off are
     * excluded — they are recorded as duty periods but consume no duty time.
     */
    List<FtlExceedanceDto> findExceedances(UUID tenantId, LocalDate from, LocalDate to);
}
