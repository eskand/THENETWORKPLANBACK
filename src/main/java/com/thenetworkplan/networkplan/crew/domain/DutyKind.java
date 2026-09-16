package com.thenetworkplan.networkplan.crew.domain;

/**
 * Nature of a duty period.
 *
 * <p>The audit found standby treated as a free day. It is a duty here, and it
 * counts as one: {@link #countsAsDuty()} is the single answer to that question,
 * so no screen can decide otherwise.
 */
public enum DutyKind {

    FLIGHT_DUTY,
    STANDBY,
    POSITIONING,
    TRAINING,
    OFFICE,
    REST,
    OFF;

    /** Whether the period consumes duty time. Rest and days off do not. */
    public boolean countsAsDuty() {
        return this != REST && this != OFF;
    }

    /** Whether the person is available to be rostered on a flight that day. */
    public boolean isAvailableForFlight() {
        return this == STANDBY || this == OFF || this == REST;
    }

    /** Roster code shown on the grid, as the operator writes it. */
    public String rosterCode() {
        return switch (this) {
            case FLIGHT_DUTY -> "FLT";
            case STANDBY -> "SBY";
            case POSITIONING -> "POS";
            case TRAINING -> "TRG";
            case OFFICE -> "OFFICE";
            case REST, OFF -> "OFF";
        };
    }
}
