package com.thenetworkplan.networkplan.roster.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RosterVersionDto(
        UUID id,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        OffsetDateTime publishedAt,
        UUID publishedBy,
        long entryCount,
        boolean editable) implements Serializable {
}
