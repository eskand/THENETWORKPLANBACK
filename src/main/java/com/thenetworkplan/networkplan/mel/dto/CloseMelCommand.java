package com.thenetworkplan.networkplan.mel.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Clearing a deferred item: the rectification is what closes it. */
public record CloseMelCommand(
        @NotBlank String correctiveAction,
        UUID closedBy) {
}
