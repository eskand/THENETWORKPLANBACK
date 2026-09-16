package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TrainingRecordDto(
        UUID id,
        UUID personId,
        String courseCode,
        String courseTitle,
        String category,
        LocalDate completedOn,
        LocalDate validTo,
        /** VALID / EXPIRING / EXPIRED / UNKNOWN, from the shared document rule. */
        String status,
        BigDecimal score,
        String instructorName,
        String reference) implements Serializable {
}
