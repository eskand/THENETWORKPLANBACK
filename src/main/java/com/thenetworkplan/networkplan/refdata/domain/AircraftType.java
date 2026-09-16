package com.thenetworkplan.networkplan.refdata.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** ICAO aircraft type with the performance figures dispatch actually reads. */
@Entity
@Table(name = "aircraft_types", schema = "refdata")
@Getter
@Setter
public class AircraftType extends BaseEntity {

    @Column(name = "icao_type", nullable = false)
    private String icaoType;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "wake_category")
    private String wakeCategory;

    @Column(name = "mtow_kg")
    private Integer mtowKg;

    @Column(name = "min_runway_ft")
    private Integer minRunwayFt;

    @Column(name = "max_pax")
    private Integer maxPax;

    @Column(name = "range_nm")
    private Integer rangeNm;

    @Column(name = "cruise_tas_kt")
    private Integer cruiseTasKt;

    @Column(name = "min_runway_m")
    private Integer minRunwayM;

    /** La motorisation, telle que le registre de flotte l'affiche. */
    @Column(name = "engines")
    private String engines;

    @Column(name = "crew_flight_deck")
    private Short crewFlightDeck;

    @Column(name = "crew_cabin")
    private Short crewCabin;

    /** « Single-pilot certified », « 2 pilots (multi-crew) » : la base de certification. */
    @Column(name = "crew_certification")
    private String crewCertification;

    /** La fiche de reference du type. Null tant qu'aucune ne lui correspond. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_id")
    private AircraftReference reference;

    @Column(name = "etops_applicable", nullable = false)
    private boolean etopsApplicable;
}
