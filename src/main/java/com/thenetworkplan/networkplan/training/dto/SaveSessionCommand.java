package com.thenetworkplan.networkplan.training.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Scheduling a run of a course. */
public record SaveSessionCommand(
        @NotBlank String courseCode,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        String location,
        @Min(1) int capacity,
        UUID instructorId) {
}
