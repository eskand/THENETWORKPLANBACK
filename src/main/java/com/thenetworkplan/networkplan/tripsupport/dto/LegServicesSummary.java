package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.util.UUID;

/** What the SERVICES column of the board needs, and nothing more. */
public record LegServicesSummary(
        UUID legId,
        int total,
        int confirmed,
        String readiness) implements Serializable {

    public static LegServicesSummary nothingRequested(UUID legId) {
        return new LegServicesSummary(legId, 0, 0, ServiceReadiness.ATTENTION.name());
    }
}
