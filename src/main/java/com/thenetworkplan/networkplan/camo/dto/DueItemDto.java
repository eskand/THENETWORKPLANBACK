package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of the due list.
 *
 * <p>The three remaining figures travel together with the limit that drives
 * them: a task can be twelve days from its calendar limit and three hundred
 * hours from its hour limit, and the planner has to see which one bites.
 */
public record DueItemDto(
        UUID taskId,
        UUID aircraftId,
        String registration,
        String icaoType,
        String code,
        String title,
        LocalDate lastDoneOn,
        LocalDate dueOn,
        BigDecimal dueAtHours,
        Integer dueAtCycles,
        BigDecimal remainingHours,
        Long remainingDays,
        Integer remainingCycles,
        String drivingLimit,
        String status) implements Serializable {
}
