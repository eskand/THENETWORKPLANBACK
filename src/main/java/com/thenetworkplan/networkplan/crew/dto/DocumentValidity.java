package com.thenetworkplan.networkplan.crew.dto;

/** Worst state among a crew member's licence, medical and recurrent training. */
public enum DocumentValidity {

    VALID,

    /** Valid on the day of the flight but expiring within the warning window. */
    EXPIRING,

    /** Expired on the day of the flight: the person cannot be rostered. */
    EXPIRED,

    /** At least one expiry date is missing from the crew file. */
    UNKNOWN;

    public int severity() {
        return switch (this) {
            case VALID -> 0;
            case UNKNOWN -> 1;
            case EXPIRING -> 2;
            case EXPIRED -> 3;
        };
    }
}
