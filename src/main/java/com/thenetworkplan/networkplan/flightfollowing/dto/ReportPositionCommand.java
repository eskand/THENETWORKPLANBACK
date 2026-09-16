package com.thenetworkplan.networkplan.flightfollowing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Recording a position received by voice or by message.
 *
 * <p>{@code provider} defaults to {@code MANUAL} and cannot be forged into
 * {@code ADSB} by omission: a position typed by a person is marked as such for
 * ever.
 */
public record ReportPositionCommand(
        @NotNull UUID aircraftId,
        UUID legId,
        @NotNull OffsetDateTime reportedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        Integer altitudeFt,
        Integer groundSpeedKt,
        Integer trackDeg,
        Integer verticalRateFpm,
        Boolean onGround,
        String provider,
        String providerRef) {
}
