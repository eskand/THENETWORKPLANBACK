package com.thenetworkplan.networkplan.flightfollowing.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PositionDto(
        UUID id,
        UUID legId,
        UUID aircraftId,
        String registration,
        OffsetDateTime reportedAt,
        OffsetDateTime receivedAt,
        long ageMinutes,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer altitudeFt,
        Integer groundSpeedKt,
        Integer trackDeg,
        Integer verticalRateFpm,
        Boolean onGround,
        String provider,
        boolean automatic,
        String providerRef) implements Serializable {
}
