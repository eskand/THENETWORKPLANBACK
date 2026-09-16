package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;

/**
 * How many legs touched one station over a window, counted by the database.
 *
 * <p>Answers the Airports Data question "which stations do we actually use",
 * which is what separates an operational directory from a reference set.
 */
public record StationCount(String icao, Long count) implements Serializable {

    public long value() {
        return count == null ? 0L : count;
    }
}
