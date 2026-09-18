package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The root of the model: one elementary flight, with a stable key.
 *
 * <p>Two decisions worth naming. First, {@link #businessKey} is unique and never
 * regenerated — the prototype rebuilt its whole programme from a seeded random
 * generator on every render, so nothing could be referenced twice. Second, the
 * aircraft is a real association: the dispatch board shows the registration and
 * the type on every row, so the query fetch-joins it rather than paying a
 * round trip per line.
 */
@Entity
@Table(name = "legs", schema = "ops")
@Getter
@Setter
public class Leg extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "flight_no", nullable = false)
    private String flightNo;

    @Column(name = "dep_icao", nullable = false)
    private String depIcao;

    @Column(name = "arr_icao", nullable = false)
    private String arrIcao;

    /** Operating base the leg is counted against, for the Base filter. */
    @Column(name = "base_icao")
    private String baseIcao;

    @Column(name = "std", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime std;

    @Column(name = "sta", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime sta;

    @Column(name = "etd", columnDefinition = "timestamptz")
    private OffsetDateTime etd;

    @Column(name = "eta", columnDefinition = "timestamptz")
    private OffsetDateTime eta;

    /** OOOI: off blocks. */
    @Column(name = "out_at", columnDefinition = "timestamptz")
    private OffsetDateTime outAt;

    /** OOOI: airborne. Distinct from OUT — the prototype used one value for both. */
    @Column(name = "off_at", columnDefinition = "timestamptz")
    private OffsetDateTime offAt;

    /** OOOI: landing. */
    @Column(name = "on_at", columnDefinition = "timestamptz")
    private OffsetDateTime onAt;

    /** OOOI: on blocks. */
    @Column(name = "in_at", columnDefinition = "timestamptz")
    private OffsetDateTime inAt;

    /** Calculated take-off time received from ATFM. */
    @Column(name = "ctot", columnDefinition = "timestamptz")
    private OffsetDateTime ctot;

    /**
     * La reference sous laquelle le creneau a ete delivre.
     *
     * <p>Un CTOT sans reference n'est pas verifiable : c'est elle qu'on cite
     * pour faire revoir un creneau, et c'est elle qui distingue un creneau recu
     * d'une heure saisie a la main.
     */
    @Column(name = "ctot_ref")
    private String ctotRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LegStatus status = LegStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "flight_type", nullable = false)
    private FlightType flightType = FlightType.PAX;

    /**
     * La nature COMMERCIALE de l'etape — programme, hors programme, prive,
     * vol d'Etat. Distincte de {@link #flightType}, qui dit ce que l'etape
     * transporte : c'est cet axe-ci qui decide de la lettre de la case 8 du
     * plan de vol.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_type", nullable = false)
    private CommercialType commercialType = CommercialType.NON_SCHEDULED;

    @Column(name = "pax_count", nullable = false)
    private int paxCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "mvt_sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime mvtSentAt;

    @Column(name = "remark")
    private String remark;

    /**
     * La note d'exploitation de l'etape — menu du dossier de vol.
     *
     * <p>Distincte de {@link #remark}, qui est la remarque du dossier
     * documentaire : l'annexe tient les deux separement ({@code _note} et
     * {@code _tripRemarks}), parce que la premiere se lit avant le depart et la
     * seconde s'ecrit apres.
     */
    @Column(name = "flight_note")
    private String flightNote;

    @Column(name = "flight_note_at", columnDefinition = "timestamptz")
    private OffsetDateTime flightNoteAt;

    @Column(name = "flight_note_by")
    private UUID flightNoteBy;

    @Column(name = "business_key", nullable = false, updatable = false)
    private String businessKey;

    /** Revised time of departure when there is one, otherwise the scheduled time. */
    public OffsetDateTime effectiveDeparture() {
        return etd != null ? etd : std;
    }

    public OffsetDateTime effectiveArrival() {
        return eta != null ? eta : sta;
    }
}
