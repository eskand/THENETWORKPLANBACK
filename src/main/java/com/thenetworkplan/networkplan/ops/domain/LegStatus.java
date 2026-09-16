package com.thenetworkplan.networkplan.ops.domain;

/**
 * Life cycle of a leg.
 *
 * <p>The audited prototype only ever produced {@code scheduled}: a flight whose
 * STA had passed still showed as scheduled, and the shift handover reported that
 * as a data-quality anomaly. Here the status advances from real events —
 * a release, an off-block time, a landing, a closure — and never from the clock
 * alone.
 */
public enum LegStatus {

    /** In the programme, preparation not finished. */
    PLANNED,

    /** Every blocking check passed, waiting for the release signature. */
    PREPARED,

    /** Released by the dispatcher and acknowledged by the commander. */
    RELEASED,

    /** Off blocks. */
    DEPARTED,

    /** On blocks at destination. */
    ARRIVED,

    /** Closed: times, delays and journey log recorded. */
    CLOSED,

    /** Cancelled. */
    CANCELLED;

    public boolean isAirborneOrLater() {
        return this == DEPARTED || this == ARRIVED || this == CLOSED;
    }

    public boolean isOpen() {
        return this != CLOSED && this != CANCELLED;
    }
}
