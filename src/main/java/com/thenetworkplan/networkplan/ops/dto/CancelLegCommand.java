package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelLegCommand(@NotBlank String reason) {
}
