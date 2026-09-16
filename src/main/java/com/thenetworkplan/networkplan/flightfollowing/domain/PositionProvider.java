package com.thenetworkplan.networkplan.flightfollowing.domain;

/**
 * Where a position came from.
 *
 * <p>The audit's finding on Flight Following was that the prototype simulated
 * positions and fabricated {@code icao24} identifiers. The provider is stored
 * on every row so a screen can never present a seeded or manual point as a
 * received signal.
 */
public enum PositionProvider {
    ADSB,
    ACARS,
    SATCOM,
    MANUAL,
    RADAR;

    /** True when the position came from a receiver rather than from a person. */
    public boolean isAutomatic() {
        return this == ADSB || this == ACARS || this == SATCOM || this == RADAR;
    }
}
