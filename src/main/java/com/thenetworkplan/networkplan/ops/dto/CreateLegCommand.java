package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Adding a leg to the programme (the "+ Add Flight" button, and DOM7 on a
 * launched quote). A leg is always created with an explicit schedule: the
 * product never invents one.
 */
public record CreateLegCommand(
        @NotBlank @Size(max = 12) String flightNo,
        @NotBlank @Size(max = 12) String registration,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code") String depIcao,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code") String arrIcao,
        @NotNull OffsetDateTime std,
        @NotNull OffsetDateTime sta,
        String flightType,
        @Min(0) int paxCount,
        @Size(max = 120) String clientRef) {
}
