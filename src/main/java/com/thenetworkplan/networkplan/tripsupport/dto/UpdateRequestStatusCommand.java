package com.thenetworkplan.networkplan.tripsupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Advancing a request through its life cycle. The reference becomes mandatory on
 * CONFIRMED: a confirmation without a reference is what nobody can audit later.
 */
public record UpdateRequestStatusCommand(
        @NotBlank String status,
        @Size(max = 120) String reference,
        @Size(max = 500) String remark) {
}
