package com.thenetworkplan.networkplan.camoadmin.dto;

import java.util.UUID;

/** A count attached to one registration, answered by a {@code group by}. */
public record AircraftCount(UUID aircraftId, Long count) {

    public int intValue() {
        return count == null ? 0 : count.intValue();
    }
}
