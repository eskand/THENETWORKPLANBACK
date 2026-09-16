package com.thenetworkplan.networkplan.camo.service;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The only way flight time and cycles reach an aircraft.
 *
 * <p>Same intent as {@code LegEventRecorder} in DOM1: there must be no path
 * that moves a counter and forgets the line that justifies it. Recording an
 * utilisation writes the row <em>and</em> advances TSN/CSN in one transaction,
 * so the counters are always the initial reading plus the sum of the rows.
 */
public interface UtilisationRecorder {

    /**
     * @param legId null for a flight recorded outside the programme
     * @return true when a row was written, false when this leg was already
     *         recorded — signing a tech log page twice must not fly the
     *         aircraft twice
     */
    boolean record(UUID tenantId,
                   Aircraft aircraft,
                   UUID legId,
                   LocalDate flownOn,
                   int blockMinutes,
                   Integer airMinutes,
                   int cycles,
                   String reference);
}
