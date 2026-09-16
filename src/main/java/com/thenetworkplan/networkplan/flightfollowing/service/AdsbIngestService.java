package com.thenetworkplan.networkplan.flightfollowing.service;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Turns live state vectors into stored positions.
 *
 * <p>One writer for one fact, like {@code LegEventRecorder} and
 * {@code WeatherService.store}: nothing else in the product writes an
 * automatic position report.
 *
 * <p>The correlation is by Mode-S address and nothing else. Not by callsign,
 * which two operators may share and which a crew can mistype; not by
 * proximity to a scheduled route, which is a guess dressed as a match. An
 * aircraft with no Mode-S code on file cannot be correlated, and the result
 * says so by name rather than reporting a quiet zero.
 */
public interface AdsbIngestService {

    /**
     * What one ingest run did, so a screen can explain itself.
     *
     * @param state        LIVE, NO_SOURCE (provider disabled) or NO_ANSWER
     * @param seen         state vectors the source returned inside the box
     * @param matched      vectors that belong to one of our aircraft
     * @param stored       positions actually written — a repeat of the same
     *                     message is not written twice
     * @param withoutModeS registrations that cannot be correlated because no
     *                     Mode-S code has been entered for them
     */
    record Result(
            String state,
            String provider,
            int seen,
            int matched,
            int stored,
            List<String> withoutModeS,
            OffsetDateTime ranAt) implements Serializable {
    }

    /** Fetch, correlate, store. Safe to call on every board read. */
    Result ingest(UUID tenantId);

    /** The last run, without fetching again. */
    Result lastRun(UUID tenantId);

    /**
     * The live traffic the source last returned inside the box.
     *
     * <p>These are other operators' aircraft, not ours: real messages from
     * real transponders, shown so the map says what is actually in the sky.
     * The audited prototype drew its own fleet as an animation and kept this
     * layer optional and off; here the two are separated by colour and named
     * in the legend, because an aircraft we are responsible for and an
     * aircraft we merely see are not the same thing.
     *
     * @param limit hard cap on the number returned — a box over Europe holds
     *              several thousand at any moment, and a screen does not need
     *              them all to be useful
     */
    List<AdsbSource.StateVector> traffic(UUID tenantId, int limit);
}
