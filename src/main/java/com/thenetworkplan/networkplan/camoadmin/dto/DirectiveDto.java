package com.thenetworkplan.networkplan.camoadmin.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DirectiveDto(
        UUID id,
        String kind,
        String reference,
        String subject,
        String issuedBy,
        LocalDate issuedOn,
        LocalDate effectiveOn,
        String icaoType,
        LocalDate complianceByDate,
        BigDecimal complianceByHours,
        String method,
        Integer recurringMonths,
        int aircraftAffected,
        int aircraftComplied,
        int aircraftOutstanding,
        /** OVERDUE when a tail is still open past the compliance date. */
        String status,
        List<DirectiveApplicationDto> applications) implements Serializable {
}
