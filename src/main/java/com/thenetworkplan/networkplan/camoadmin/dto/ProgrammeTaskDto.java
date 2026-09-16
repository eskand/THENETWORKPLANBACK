package com.thenetworkplan.networkplan.camoadmin.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record ProgrammeTaskDto(
        UUID id,
        String icaoType,
        String code,
        String title,
        String ataChapter,
        BigDecimal intervalHours,
        Integer intervalCycles,
        Integer intervalMonths,
        BigDecimal toleranceHours,
        Integer toleranceDays,
        boolean mandatory,
        String reference,
        /** How many registrations of that type carry the task today. */
        int appliedToAircraft) implements Serializable {
}
