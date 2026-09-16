package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;

/**
 * One row of the Airports Data list.
 *
 * <p>{@code legsLast90Days} is what turns a reference set into an operational
 * directory: the stations the operator actually uses come first, and the count
 * is a query over {@code ops.legs}, not a favourite flag someone maintains.
 */
public record AirportRowDto(
        AirportDto airport,
        int runways,
        int longestRunwayFt,
        int notesInForce,
        String worstNoteSeverity,
        int suppliers,
        long legsLast90Days) implements Serializable {
}
