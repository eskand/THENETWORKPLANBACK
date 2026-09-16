package com.thenetworkplan.networkplan.config.cache;

/** Single place where cache names live, so {@code @Cacheable} never uses a literal. */
public final class CacheNames {

    /** Airport reference rows, keyed by ICAO. Changes on an AIP cycle. */
    public static final String AIRPORTS = "airports";

    /** Aircraft type performance rows, keyed by ICAO type. Changes on an AFM revision. */
    public static final String AIRCRAFT_TYPES = "aircraftTypes";

    /** The tenant fleet with its airworthiness status. Short TTL: AOG must show up fast. */
    public static final String FLEET = "fleet";

    /** The rendered dispatch board. Very short TTL: it is a live operational view. */
    public static final String DISPATCH_BOARD = "dispatchBoard";

    private CacheNames() {
    }
}
