package com.thenetworkplan.networkplan.ops.domain;

/** What happened to a leg. The list is append-only, like the table. */
public enum LegEventKind {
    CREATED,
    MOVED,
    AIRCRAFT_CHANGED,
    DELAYED,
    CANCELLED,
    RELEASED,
    MOVEMENT,
    CLOSED,
    REMARK
}
