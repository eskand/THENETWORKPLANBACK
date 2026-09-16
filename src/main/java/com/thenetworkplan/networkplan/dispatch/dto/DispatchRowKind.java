package com.thenetworkplan.networkplan.dispatch.dto;

/**
 * The board mixes two kinds of row.
 *
 * <p>The screen's first lines are not flights: they are tails that cannot fly.
 * Modelling them as their own kind, sourced from DOM5, is why they can carry a
 * real reason ("awaiting parts: APU starter-generator") instead of a fake leg.
 */
public enum DispatchRowKind {
    FLIGHT,
    GROUND
}
