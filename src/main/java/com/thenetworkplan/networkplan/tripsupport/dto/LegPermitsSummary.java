package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.util.UUID;

/** What the permit indicator of the board needs. */
public record LegPermitsSummary(
        UUID legId,
        int total,
        int outstanding) implements Serializable {

    public static LegPermitsSummary none(UUID legId) {
        return new LegPermitsSummary(legId, 0, 0);
    }

    public boolean clear() {
        return outstanding == 0;
    }
}
