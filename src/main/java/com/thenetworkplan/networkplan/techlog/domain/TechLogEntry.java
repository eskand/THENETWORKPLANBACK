package com.thenetworkplan.networkplan.techlog.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.crew.domain.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One page of the electronic tech log: one flight, one aircraft. */
@Entity
@Table(name = "tech_log_entries", schema = "camo")
@Getter
@Setter
public class TechLogEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    /** Plain identifier: DOM5 does not join DOM1. */
    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "page_ref", nullable = false)
    private String pageRef;

    @Column(name = "flown_on", nullable = false)
    private LocalDate flownOn;

    @Column(name = "dep_icao")
    private String depIcao;

    @Column(name = "arr_icao")
    private String arrIcao;

    @Column(name = "block_minutes")
    private Integer blockMinutes;

    @Column(name = "air_minutes")
    private Integer airMinutes;

    @Column(name = "cycles", nullable = false)
    private int cycles = 1;

    @Column(name = "fuel_uplift_litres")
    private BigDecimal fuelUpliftLitres;

    @Column(name = "oil_added_litres")
    private BigDecimal oilAddedLitres;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commander_id")
    private Person commander;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id")
    private Person engineer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TechLogStatus status = TechLogStatus.OPEN;

    @Column(name = "signed_at", columnDefinition = "timestamptz")
    private OffsetDateTime signedAt;

    @Column(name = "remark")
    private String remark;
}
