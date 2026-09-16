package com.thenetworkplan.networkplan.airworthiness.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AircraftDto(
        UUID id,
        String registration,
        String icaoType,
        String model,
        Integer maxPax,
        Integer minRunwayFt,
        String homeBaseIcao,
        String currentBaseIcao,
        String status,
        String statusReason,
        OffsetDateTime statusSince,
        BigDecimal hoursSinceNew,
        Integer cyclesSinceNew,
        String nextCheckLabel,
        OffsetDateTime nextCheckDueAt) implements Serializable {
}
