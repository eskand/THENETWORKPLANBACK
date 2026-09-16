package com.thenetworkplan.networkplan.tripsupport.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Permit verdict for one state on one leg, with the instrument it rests on and
 * the version of the ASA corpus that produced it.
 *
 * <p>The {@code firs} array column is deliberately not mapped here: the crossed
 * FIRs are produced by the route engine (DOM2 C6) and read through its API, not
 * through this aggregate.
 */
@Entity
@Table(name = "country_status", schema = "tripsupport")
@Getter
@Setter
public class CountryStatus extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Column(name = "country_iso2", nullable = false)
    private String countryIso2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CountryPermitStatus status;

    @Column(name = "instrument_ref")
    private String instrumentRef;

    @Column(name = "lead_time_hours")
    private Integer leadTimeHours;

    @Column(name = "deadline_at", columnDefinition = "timestamptz")
    private OffsetDateTime deadlineAt;

    @Column(name = "asa_corpus_version")
    private String asaCorpusVersion;
}
