package com.thenetworkplan.networkplan.camo.service.impl;

import com.thenetworkplan.networkplan.camo.domain.AircraftTask;
import com.thenetworkplan.networkplan.camo.domain.DueStatus;
import com.thenetworkplan.networkplan.camo.service.MaintenanceDueRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * The tightest of the three limits decides.
 *
 * <p>Warning windows are the operator's usual ones: thirty days, fifty flight
 * hours, one hundred cycles. They are constants here rather than configuration
 * because they only affect the colour of a row — the limit itself is the one
 * written on the task, and no window can make an overdue task look planned.
 */
@Component
public class MaintenanceDueRuleImpl implements MaintenanceDueRule {

    private static final long WARNING_DAYS = 30;
    private static final BigDecimal WARNING_HOURS = BigDecimal.valueOf(50);
    private static final int WARNING_CYCLES = 100;

    @Override
    public Verdict evaluate(AircraftTask task, BigDecimal currentHours, Integer currentCycles, LocalDate today) {
        Long remainingDays = task.getDueOn() == null
                ? null
                : ChronoUnit.DAYS.between(today, task.getDueOn());

        BigDecimal remainingHours = (task.getDueAtHours() == null || currentHours == null)
                ? null
                : task.getDueAtHours().subtract(currentHours);

        Integer remainingCycles = (task.getDueAtCycles() == null || currentCycles == null)
                ? null
                : task.getDueAtCycles() - currentCycles;

        if (remainingDays == null && remainingHours == null && remainingCycles == null) {
            // No limit on file is not "in date": it is unknown, and it shows as such.
            return new Verdict(DueStatus.UNKNOWN, null, null, null, "NONE");
        }

        DueStatus status = DueStatus.PLANNED;
        String driving = "NONE";

        if (remainingDays != null) {
            if (remainingDays < 0) {
                status = DueStatus.OVERDUE;
                driving = "DATE";
            } else if (remainingDays <= WARNING_DAYS) {
                status = DueStatus.DUE_SOON;
                driving = "DATE";
            } else {
                driving = "DATE";
            }
        }
        if (remainingHours != null) {
            if (remainingHours.signum() < 0) {
                status = DueStatus.OVERDUE;
                driving = "HOURS";
            } else if (remainingHours.compareTo(WARNING_HOURS) <= 0 && status != DueStatus.OVERDUE) {
                status = DueStatus.DUE_SOON;
                driving = "HOURS";
            } else if (status == DueStatus.PLANNED && tighterInHours(remainingHours, remainingDays)) {
                driving = "HOURS";
            }
        }
        if (remainingCycles != null) {
            if (remainingCycles < 0) {
                status = DueStatus.OVERDUE;
                driving = "CYCLES";
            } else if (remainingCycles <= WARNING_CYCLES && status != DueStatus.OVERDUE) {
                status = DueStatus.DUE_SOON;
                driving = "CYCLES";
            }
        }

        return new Verdict(status, remainingHours, remainingDays, remainingCycles, driving);
    }

    /**
     * Rough comparison used only to label which limit is closest when nothing is
     * near its window: fifty flight hours is roughly a fortnight of utilisation
     * on this fleet.
     */
    private boolean tighterInHours(BigDecimal remainingHours, Long remainingDays) {
        if (remainingDays == null) {
            return true;
        }
        return remainingHours.doubleValue() / 3.5 < remainingDays;
    }
}
