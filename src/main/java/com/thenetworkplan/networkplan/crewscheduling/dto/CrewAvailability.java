package com.thenetworkplan.networkplan.crewscheduling.dto;

/**
 * Why a crew member can or cannot take a seat.
 *
 * <p>Ordered by severity, worst last, the way {@code DocumentValidity} is: the
 * checker returns the worst finding and the board colours the row by it.
 */
public enum CrewAvailability {

    AVAILABLE,

    /** Rest shorter than the operator threshold — a warning, not a verdict. */
    REST_SHORT,

    /** Already holds a seat on an overlapping leg. */
    ALREADY_ASSIGNED,

    /** No valid type rating on the aircraft of this leg. */
    NOT_QUALIFIED,

    /** Leave, sickness or any other declared absence covering the day. */
    ABSENT,

    /** Licence, medical or recurrent training expired on the day of the flight. */
    DOCUMENTS_EXPIRED;

    public int severity() {
        return ordinal();
    }

    /** Whether the finding stops an assignment from being written. */
    public boolean blocksAssignment() {
        return this == NOT_QUALIFIED || this == ABSENT
                || this == DOCUMENTS_EXPIRED || this == ALREADY_ASSIGNED;
    }
}
