package com.thenetworkplan.networkplan.tripsupport.dto;

/**
 * The three states of the SERVICES column.
 *
 * <p>Derived from the actual request rows, not from a random boolean: the audit's
 * finding was that "Services Ready" rested on {@code mvtSent = rng() > 0.25}.
 */
public enum ServiceReadiness {

    /** Every request at both stations is confirmed. */
    READY,

    /** At least one request is sent or acknowledged but not confirmed. */
    PENDING,

    /** Nothing requested yet, or a request was refused. */
    ATTENTION;

    public static ServiceReadiness of(int total, int confirmed, boolean anyRefused) {
        if (total == 0 || anyRefused) {
            return ATTENTION;
        }
        return confirmed == total ? READY : PENDING;
    }
}
