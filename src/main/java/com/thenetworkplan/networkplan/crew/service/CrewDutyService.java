package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.crew.dto.CrewDutyDto;
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
}
