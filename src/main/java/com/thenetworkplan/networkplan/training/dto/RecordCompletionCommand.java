package com.thenetworkplan.networkplan.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recording a course completed elsewhere (an external school, a previous
 * operator). {@code validTo} is deliberately absent: it is derived from the
 * course by {@code TrainingValidityRule} and never supplied by the caller.
 */
public record RecordCompletionCommand(
        @NotNull UUID personId,
        @NotBlank String courseCode,
        @NotNull LocalDate completedOn,
        BigDecimal score,
        UUID instructorId,
        String reference) {
}
