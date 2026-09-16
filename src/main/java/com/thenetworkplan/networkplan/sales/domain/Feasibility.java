package com.thenetworkplan.networkplan.sales.domain;

/**
 * Whether the operator can actually fly the request.
 *
 * <p>{@code UNKNOWN} is the value a request carries until somebody looks. It
 * is not {@code FEASIBLE} by default — quoting a trip nobody checked is how an
 * operator ends up cancelling on the morning of departure.
 */
public enum Feasibility {
    FEASIBLE,
    NOT_FEASIBLE,
    CONDITIONAL,
    UNKNOWN;

    public boolean allowsQuoting() {
        return this == FEASIBLE || this == CONDITIONAL;
    }
}
