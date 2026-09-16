package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The commander's acknowledgement, which is what makes a release cosigned. */
public record AcknowledgeReleaseCommand(@NotNull UUID acknowledgedBy) {
}
