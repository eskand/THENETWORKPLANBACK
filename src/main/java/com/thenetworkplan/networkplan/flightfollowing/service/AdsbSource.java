package com.thenetworkplan.networkplan.flightfollowing.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A live source of aircraft state vectors.
 *
 * <p>One interface, so the provider can change without any screen knowing.
 * The contract is deliberately thin: give back what was received, decoded but
 * not interpreted. Correlating a state vector with a leg is somebody else's
 * job, and it is the job that has to be able to say "no match".
 */
public interface AdsbSource {

    /** OPENSKY, and whatever comes next. Stored on every row it produces. */
    String provider();

    boolean isEnabled();

    /**
     * One state vector, as received.
     *
     * @param modeSHex   the ICAO 24-bit address, lowercase hex — the only
     *                   thing that can tie this message to an aircraft
     * @param callsign   trimmed, or null when the transponder sent none
     * @param altitudeFt barometric altitude, or null when not transmitted
     * @param onGround   as reported; null when the message did not say
     */
    record StateVector(
            String modeSHex,
            String callsign,
            double latitude,
            double longitude,
            Integer altitudeFt,
            Integer groundSpeedKt,
            Integer trackDeg,
            Integer verticalRateFpm,
            Boolean onGround,
            OffsetDateTime reportedAt) {
    }

    /**
     * Everything the source can see inside the configured box.
     *
     * <p>Returns an empty list on failure rather than throwing: a source that
     * did not answer leaves the last known positions in place, and the screen
     * reports their age. It never invents a position.
     */
    List<StateVector> fetch();
}
