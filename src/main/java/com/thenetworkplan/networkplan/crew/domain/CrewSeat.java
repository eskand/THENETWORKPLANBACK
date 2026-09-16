package com.thenetworkplan.networkplan.crew.domain;

/**
 * Seat filled on a given leg.
 *
 * <p>The prototype hard-coded three seats. Additional cabin and an engineer seat
 * are modelled here so a Lineage crew is not squeezed into a Citation shape.
 */
public enum CrewSeat {
    CPT,
    FO,
    CABIN_1,
    CABIN_2,
    ENGINEER;

    /** Seats without which the leg cannot be released. */
    public boolean isFlightDeck() {
        return this == CPT || this == FO;
    }
}
