package com.thenetworkplan.networkplan.airworthiness.domain;

/** Airworthiness state of a registration, as CAMO releases it. */
public enum AircraftStatus {

    /** Released to service. */
    SERVICEABLE,

    /** Planned maintenance, out of the programme for a known window. */
    MAINTENANCE,

    /** Aircraft on ground: unplanned, no release to service. */
    AOG;

    public boolean isFlyable() {
        return this == SERVICEABLE;
    }
}
