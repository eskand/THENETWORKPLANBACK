package com.thenetworkplan.networkplan.ops.domain;

/**
 * The four OOOI events, in order.
 *
 * <p>Four distinct times, not one ATD used twice: block times feed the journey
 * log and the airframe counters, and airborne time feeds the MVT message.
 */
public enum MovementKind {

    /** Off blocks. */
    OUT,

    /** Airborne. */
    OFF,

    /** Landed. */
    ON,

    /** On blocks. */
    IN
}
