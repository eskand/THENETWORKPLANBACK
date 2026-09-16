package com.thenetworkplan.networkplan.airworthiness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Declaring or clearing an AOG. The reason is mandatory when the aircraft leaves
 * service: an unexplained AOG is what the OCC cannot act on.
 */
public record ChangeAircraftStatusCommand(
        @NotNull String status,
        @NotBlank String reason) {
}
