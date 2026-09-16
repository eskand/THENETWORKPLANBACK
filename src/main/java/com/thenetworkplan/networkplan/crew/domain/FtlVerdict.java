package com.thenetworkplan.networkplan.crew.domain;

/**
 * Result of the flight-time-limitation check for one assignment.
 *
 * <p>{@link #UNKNOWN} is a first-class value, not a default to hide behind: the
 * audit's principle is that a missing input produces UNKNOWN, never a green
 * verdict.
 */
public enum FtlVerdict {

    /** Legal with margin. */
    OK,

    /** Legal but close to a limit, or relying on a reduced rest / discretion. */
    WARNING,

    /** Outside the limits of the tenant authority: the leg cannot be released. */
    BREACH,

    /** Not enough data to decide. */
    UNKNOWN;

    /** Ordering used to fold several assignments into one verdict for a leg. */
    public int severity() {
        return switch (this) {
            case OK -> 0;
            case UNKNOWN -> 1;
            case WARNING -> 2;
            case BREACH -> 3;
        };
    }
}
