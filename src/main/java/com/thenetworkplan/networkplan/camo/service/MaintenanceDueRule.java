package com.thenetworkplan.networkplan.camo.service;

import com.thenetworkplan.networkplan.camo.domain.AircraftTask;
import com.thenetworkplan.networkplan.camo.domain.DueStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How close a task is to its limit, given the current counters.
 *
 * <p>A rule with no repository and no I/O, like {@code CrewDocumentChecker}:
 * the three limits are compared and the tightest one wins, which is the whole
 * of the Part-M question and is worth being unit-testable on its own.
 */
public interface MaintenanceDueRule {

    /**
     * @param currentHours  hours since new of the aircraft, now
     * @param currentCycles cycles since new of the aircraft, now
     * @param today         date the comparison is made against
     */
    Verdict evaluate(AircraftTask task, BigDecimal currentHours, Integer currentCycles, LocalDate today);

    /**
     * @param remainingHours hours left on the tightest hour limit, null when none
     * @param remainingDays  days left on the date limit, null when none
     * @param remainingCycles cycles left, null when none
     * @param drivingLimit   which of the three decided the verdict
     */
    record Verdict(DueStatus status,
                   BigDecimal remainingHours,
                   Long remainingDays,
                   Integer remainingCycles,
                   String drivingLimit) {
    }
}
