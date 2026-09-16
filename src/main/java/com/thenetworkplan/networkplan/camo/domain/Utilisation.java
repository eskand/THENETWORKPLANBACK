package com.thenetworkplan.networkplan.camo.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * What one aircraft flew, one line per flight.
 *
 * <p>This is the table the audit's "TSN/CSN never fed" finding is about: the
 * counters on {@code camo.aircraft} are the initial reading plus the sum of
 * these rows, and {@code UtilisationRecorder} is the only writer — so no path
 * can add flight time without leaving the line that justifies it.
 */
@Entity
@Table(name = "utilisation", schema = "camo")
@Getter
@Setter
public class Utilisation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    /** Null for a flight recorded outside the operational programme. */
    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "flown_on", nullable = false)
    private LocalDate flownOn;

    @Column(name = "block_minutes", nullable = false)
    private int blockMinutes;

    @Column(name = "air_minutes")
    private Integer airMinutes;

    @Column(name = "cycles", nullable = false)
    private int cycles = 1;
}
