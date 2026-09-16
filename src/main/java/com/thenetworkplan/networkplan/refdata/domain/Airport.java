package com.thenetworkplan.networkplan.refdata.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Aerodrome reference row.
 *
 * <p>ICAO is the key everywhere in the model; IATA is carried only so the screens
 * can show the code operators read. The audit found the prototype mixed the two
 * in the same field (TUN next to DTTA for the same airport) — that is fixed here
 * by storing one and deriving the other.
 */
@Entity
@Table(name = "airports", schema = "refdata")
@Getter
@Setter
public class Airport extends BaseEntity {

    @Column(name = "icao", nullable = false)
    private String icao;

    @Column(name = "iata")
    private String iata;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city")
    private String city;

    @Column(name = "country_iso2", nullable = false)
    private String countryIso2;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "elevation_ft")
    private Integer elevationFt;

    @Column(name = "longest_runway_ft")
    private Integer longestRunwayFt;

    @Column(name = "aerodrome_category")
    private String aerodromeCategory;

    @Column(name = "rffs_category")
    private String rffsCategory;

    @Column(name = "time_zone")
    private String timeZone;

    /* --- l annuaire (V50) ---
       Ce qu un exploitant lit sur une fiche d aerodrome ou il ne va pas
       encore : un deroutement se prepare sur un terrain qui n est dans
       aucun programme. */

    /** 1 a 7 : la decoupe regionale de l annuaire. */
    @Column(name = "region")
    private Short region;

    @Column(name = "traffic_level")
    private String trafficLevel;

    @Column(name = "fuel_type")
    private String fuelType;

    /** Publiee telle quelle : « 9 », « 5/7 ». Du texte, pas un entier. */
    @Column(name = "fire_category")
    private String fireCategory;

    @Column(name = "operating_hours")
    private String operatingHours;

    @Column(name = "slot_required", nullable = false)
    private boolean slotRequired;

    @Column(name = "slot_regime")
    private String slotRegime;

    @Column(name = "restrictions")
    private String restrictions;

    /**
     * La ligne de pistes telle que l AIP la publie.
     *
     * <p>Coexiste avec {@code refdata.runways} : celui-ci porte la geometrie
     * saisie des terrains desservis, celle-ci porte le reste de l annuaire.
     */
    @Column(name = "runway_remark")
    private String runwayRemark;
}
