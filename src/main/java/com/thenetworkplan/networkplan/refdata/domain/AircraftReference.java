package com.thenetworkplan.networkplan.refdata.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * One aircraft type, as the industry publishes it.
 *
 * <p>Three hundred and eight of them, reconciled field by field between ICAO
 * Doc 8643 (descriptor, wake category, persons on board) and manufacturer data
 * (masses, range, Mach, true airspeed, take-off distance). This is the
 * reference the whole product reads: a commercial feasibility study on a type
 * the operator does not own has to find it somewhere, and so does an aerodrome
 * suitability check.
 *
 * <p><b>Distinct from {@link AircraftType}.</b> That table holds what the
 * operator flies — nine rows, each with a tail count behind it. This one holds
 * what is true of a type whether anyone flies it or not. Merging them would
 * mean either a reference limited to the current fleet, or a fleet list with
 * two hundred and ninety-nine phantom entries.
 *
 * <p><b>Why the key is not the ICAO designator.</b> Twenty-five designators
 * carry more than one model — F2TH covers both the Falcon 2000 and the Falcon
 * 2000LXS, which do not get airborne in the same distance — and five records
 * have no designator at all.
 */
@Entity
@Table(name = "aircraft_reference", schema = "refdata")
@Getter
@Setter
public class AircraftReference extends BaseEntity {

    @Column(name = "manufacturer", nullable = false)
    private String manufacturer;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "icao_type")
    private String icaoType;

    /** Doc 8643 descriptor: L2J is a light twin-jet. */
    @Column(name = "descriptor")
    private String descriptor;

    @Column(name = "wake_category")
    private String wakeCategory;

    /**
     * Whether the wake category was published or derived.
     *
     * <p>The category is a function of the maximum take-off mass by ICAO
     * definition, so computing it where Doc 8643 is silent invents nothing —
     * but the reader is entitled to know which of the two they are looking at.
     */
    @Column(name = "wake_source")
    private String wakeSource;

    @Column(name = "mtow_kg")
    private Integer mtowKg;

    @Column(name = "mzfw_kg")
    private Integer mzfwKg;

    @Column(name = "mlw_kg")
    private Integer mlwKg;

    /** Dry operating weight: what it weighs before the first passenger. */
    @Column(name = "dow_kg")
    private Integer dowKg;

    @Column(name = "fuel_kg")
    private Integer fuelKg;

    @Column(name = "fuel_l")
    private Integer fuelL;

    /** Seats installed. Not the same question as {@link #pob}. */
    @Column(name = "seats")
    private Integer seats;

    /**
     * Persons on board per Doc 8643, as published: "19", "15-18", "2+9".
     *
     * <p>Kept as text because that is what the source says. The single number a
     * capacity check needs is derived by {@link #maxPersons()}, in one place,
     * rather than parsed differently by each screen that shows it.
     */
    @Column(name = "pob")
    private String pob;

    @Column(name = "range_nm")
    private Integer rangeNm;

    @Column(name = "range_max_payload_nm")
    private Integer rangeMaxPayloadNm;

    @Column(name = "range_full_pax_nm")
    private Integer rangeFullPaxNm;

    @Column(name = "range_max_fuel_nm")
    private Integer rangeMaxFuelNm;

    /** Null for a turboprop: it does not cruise in Mach. */
    @Column(name = "mach")
    private BigDecimal mach;

    @Column(name = "cruise_tas_kt")
    private Integer cruiseTasKt;

    @Column(name = "optimum_fl")
    private Integer optimumFl;

    /** Take-off distance at MTOW — which is what "minimum runway" means. */
    @Column(name = "takeoff_distance_m")
    private Integer takeoffDistanceM;

    @Column(name = "rffs_category")
    private Integer rffsCategory;

    @Column(name = "etops_minutes", columnDefinition = "integer[]")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    private Integer[] etopsMinutes;

    /**
     * The upper bound of {@link #pob}, or null when nothing is published.
     *
     * <p>"2+9" is eleven — a crew of two and nine passengers. "108-133" is a
     * hundred and thirty-three: the densest certified layout is the one a
     * capacity check must not exceed. "19" is nineteen.
     */
    public Integer maxPersons() {
        if (pob == null || pob.isBlank()) {
            return null;
        }
        String value = pob.trim();
        Matcher sum = CREW_PLUS_PAX.matcher(value);
        if (sum.matches()) {
            return Integer.parseInt(sum.group(1)) + Integer.parseInt(sum.group(2));
        }
        Matcher range = LAYOUT_RANGE.matcher(value);
        if (range.find()) {
            return Integer.parseInt(range.group(2));
        }
        Matcher one = ANY_NUMBER.matcher(value);
        return one.find() ? Integer.parseInt(one.group(1)) : null;
    }

    /** "2+9" — a crew of two and nine passengers, eleven on board. */
    private static final Pattern CREW_PLUS_PAX = Pattern.compile("^(\\d+)\\s*\\+\\s*(\\d+)$");

    /** "108-133" — the densest certified layout is the one a check must not exceed. */
    private static final Pattern LAYOUT_RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");

    private static final Pattern ANY_NUMBER = Pattern.compile("(\\d+)");
}
