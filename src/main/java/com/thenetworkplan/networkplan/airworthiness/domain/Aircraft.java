package com.thenetworkplan.networkplan.airworthiness.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A registration in the tenant fleet, with the counters and the status CAMO owns.
 *
 * <p>The audit found two competing truths for the fleet in the prototype
 * ({@code camoFleet} against {@code TNPCAMO}) and counters that were never fed.
 * This table is the only truth, and TSN/CSN are columns, not a hash.
 */
@Entity
@Table(name = "aircraft", schema = "camo")
@Getter
@Setter
public class Aircraft extends BaseEntity {

    @Column(name = "registration", nullable = false)
    private String registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_type_id", nullable = false)
    private AircraftType aircraftType;

    @Column(name = "home_base_icao")
    private String homeBaseIcao;

    @Column(name = "current_base_icao")
    private String currentBaseIcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AircraftStatus status = AircraftStatus.SERVICEABLE;

    @Column(name = "status_reason")
    private String statusReason;

    /**
     * ICAO 24-bit address, six lowercase hex characters, or null.
     *
     * <p>The only key that ties this aircraft to an ADS-B message. Null means
     * the operator has not entered it, and the aircraft simply cannot be
     * correlated — the audited prototype derived one from a hash of the
     * registration, so its own fleet could never match the live feed it was
     * reading.
     */
    @Column(name = "mode_s_hex")
    private String modeSHex;

    /** CERTIFICATE, OPERATOR or OBSERVED. Null when the code is null. */
    @Column(name = "mode_s_source")
    private String modeSSource;

    @Column(name = "status_since", columnDefinition = "timestamptz")
    private OffsetDateTime statusSince;

    @Column(name = "hours_since_new")
    private BigDecimal hoursSinceNew;

    @Column(name = "cycles_since_new")
    private Integer cyclesSinceNew;

    @Column(name = "next_check_label")
    private String nextCheckLabel;

    @Column(name = "next_check_due_at", columnDefinition = "timestamptz")
    private OffsetDateTime nextCheckDueAt;
}
