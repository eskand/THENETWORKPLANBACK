package com.thenetworkplan.networkplan.camo.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Signing off a maintenance task.
 *
 * <p>The next due date and the next due hours are <em>not</em> in the command:
 * they are computed from the programme interval and written by the service. A
 * caller that could set its own next due could quietly extend a limit.
 */
public record CompleteTaskCommand(
        @NotNull LocalDate completedOn,
        BigDecimal atHours,
        Integer atCycles,
        String workOrderRef,
        String remark) {
}
