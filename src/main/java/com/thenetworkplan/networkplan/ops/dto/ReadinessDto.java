package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * API3 — the list of what is missing before a leg can be released, split by what
 * stops the flight and what a dispatcher may derogate.
 *
 * <p>This replaces the prototype's readiness, which was three booleans, two of
 * them randomly generated.
 */
public record ReadinessDto(
        UUID legId,
        boolean releasable,
        List<ReadinessItem> blocking,
        List<ReadinessItem> derogable,
        List<ReadinessItem> info) implements Serializable {
}
