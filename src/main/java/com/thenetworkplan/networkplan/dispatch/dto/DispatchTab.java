package com.thenetworkplan.networkplan.dispatch.dto;

import java.util.Arrays;

/** The tabs of the dispatch list, exactly as the screen shows them. */
public enum DispatchTab {

    ALL_FLIGHTS,

    /**
     * Everything a dispatcher has to touch before it flies: a ground aircraft, a
     * service or permit not confirmed, an incomplete crew, an FTL margin.
     */
    NEEDS_ACTION,

    SCHEDULED,

    EN_ROUTE,

    DELAYED,

    AOG_MAINTENANCE;

    public static DispatchTab parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL_FLIGHTS;
        }
        String normalised = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return Arrays.stream(values())
                .filter(tab -> tab.name().equals(normalised))
                .findFirst()
                .orElse(ALL_FLIGHTS);
    }
}
