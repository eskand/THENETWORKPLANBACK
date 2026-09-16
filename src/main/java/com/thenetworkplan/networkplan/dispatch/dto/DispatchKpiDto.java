package com.thenetworkplan.networkplan.dispatch.dto;

import java.io.Serializable;

/**
 * The six tiles of the dispatch header.
 *
 * <p>Every figure is counted from persisted rows. In the audited prototype
 * "Services Ready" rested on a random boolean and "cancelled today" was the
 * literal zero; here a tile is the answer to a query and nothing else.
 *
 * @param flightsToday      legs whose STD falls on the requested day
 * @param tails             distinct registrations flying that day
 * @param fleetSize         registrations on the operator certificate, flying
 *                          or not — the denominator of fleet availability.
 *                          Distinct from {@code tails} on purpose: subtracting
 *                          the grounded aircraft from the tails that flew
 *                          counts them twice, since a grounded tail did not fly
 * @param servicesReady     legs with every ground service confirmed
 * @param servicesPending   legs with at least one service not confirmed
 * @param permitsOutstanding permit requests not yet granted, across the day
 * @param crewUnassigned    legs without a complete flight deck
 * @param delaysAndAog      aircraft on ground plus legs running late
 */
public record DispatchKpiDto(
        int flightsToday,
        int tails,
        int fleetSize,
        /**
         * The same figures for the day before, so today has something to be
         * read against. Counted from the same table, never estimated: a day
         * with no programme yesterday returns zero, and the screen says "no
         * comparison" rather than inventing a trend.
         */
        int flightsYesterday,
        int delayedYesterday,
        /** Measured on-time performance today; null when nothing has departed. */
        Integer otpPercent,
        /** How many departures the figure rests on. Zero means no sample. */
        int otpSample,
        Integer otpYesterdayPercent,
        int otpTargetPercent,
        int servicesReady,
        int servicesPending,
        int permitsOutstanding,
        int crewUnassigned,
        int delaysAndAog,
        int aog,
        int maintenance,
        int delayed,
        int needsAction) implements Serializable {
}
