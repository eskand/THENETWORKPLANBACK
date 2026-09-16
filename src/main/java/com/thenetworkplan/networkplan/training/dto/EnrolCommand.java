package com.thenetworkplan.networkplan.training.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Booking one crew member on one session. */
public record EnrolCommand(@NotNull UUID personId) {
}
