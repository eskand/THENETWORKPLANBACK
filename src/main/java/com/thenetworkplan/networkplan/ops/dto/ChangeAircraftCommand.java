package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Swapping the tail. Refused when the target aircraft is not released to service
 * or carries a MEL item that blocks dispatch (FR38).
 */
public record ChangeAircraftCommand(
        @NotBlank String registration,
        @NotBlank String reason) {
}
