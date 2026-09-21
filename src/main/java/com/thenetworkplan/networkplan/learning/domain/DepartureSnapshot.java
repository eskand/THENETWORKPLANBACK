package com.thenetworkplan.networkplan.learning.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * La « photo a H-1 » d'un depart — ce que l'on savait une heure avant le
 * depart programme, fige a cet instant, puis le resultat quand il est connu.
 *
 * <p>Voir V63 : aucune colonne n'est recalculee apres la prise. Le retard de la
 * jambe precedente n'est present que si l'avion etait deja pose, le METAR est
 * le dernier publie avant l'instant, perime au-dela de deux heures.
 */
@Entity
@Table(name = "departure_snapshots", schema = "ops")
@Getter
@Setter
public class DepartureSnapshot extends BaseEntity {

    @Column(name = "leg_id", nullable = false, updatable = false)
    private UUID legId;

    @Column(name = "taken_at", nullable = false, updatable = false)
    private OffsetDateTime takenAt;

    @Column(name = "lead_minutes", nullable = false)
    private int leadMinutes;

    @Column(name = "flight_no")
    private String flightNo;

    @Column(name = "registration")
    private String registration;

    @Column(name = "icao_type")
    private String icaoType;

    @Column(name = "dep_icao", nullable = false)
    private String depIcao;

    @Column(name = "arr_icao", nullable = false)
    private String arrIcao;

    @Column(name = "std", nullable = false)
    private OffsetDateTime std;

    @Column(name = "sta", nullable = false)
    private OffsetDateTime sta;

    @Column(name = "leg_index")
    private Integer legIndex;

    @Column(name = "sched_turnaround_min")
    private Integer schedTurnaroundMin;

    @Column(name = "inbound_known", nullable = false)
    private boolean inboundKnown;

    @Column(name = "inbound_delay_min")
    private Integer inboundDelayMin;

    @Column(name = "dep_congestion", nullable = false)
    private int depCongestion;

    @Column(name = "arr_congestion", nullable = false)
    private int arrCongestion;

    @Column(name = "permits_total")
    private Integer permitsTotal;

    @Column(name = "permits_outstanding")
    private Integer permitsOutstanding;

    @Column(name = "services_total")
    private Integer servicesTotal;

    @Column(name = "services_confirmed")
    private Integer servicesConfirmed;

    @Column(name = "services_readiness")
    private String servicesReadiness;

    @Column(name = "crew_complete")
    private Boolean crewComplete;

    @Column(name = "crew_ftl_status")
    private String crewFtlStatus;

    @Column(name = "crew_document_status")
    private String crewDocumentStatus;

    @Column(name = "mel_open", nullable = false)
    private int melOpen;

    @Column(name = "mel_blocking", nullable = false)
    private boolean melBlocking;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "risk_index")
    private Integer riskIndex;

    @Column(name = "dep_wx_observed_at")
    private OffsetDateTime depWxObservedAt;

    @Column(name = "dep_wx_age_min")
    private Integer depWxAgeMin;

    @Column(name = "dep_wind_dir_deg")
    private Integer depWindDirDeg;

    @Column(name = "dep_wind_kt")
    private Integer depWindKt;

    @Column(name = "dep_wind_gust_kt")
    private Integer depWindGustKt;

    @Column(name = "dep_visibility_m")
    private Integer depVisibilityM;

    @Column(name = "dep_ceiling_ft")
    private Integer depCeilingFt;

    @Column(name = "dep_cavok")
    private Boolean depCavok;

    @Column(name = "dep_conditions")
    private String depConditions;

    @Column(name = "dep_flight_category")
    private String depFlightCategory;

    @Column(name = "dep_wx_raw")
    private String depWxRaw;

    @Column(name = "arr_wx_observed_at")
    private OffsetDateTime arrWxObservedAt;

    @Column(name = "arr_wx_age_min")
    private Integer arrWxAgeMin;

    @Column(name = "arr_wind_dir_deg")
    private Integer arrWindDirDeg;

    @Column(name = "arr_wind_kt")
    private Integer arrWindKt;

    @Column(name = "arr_wind_gust_kt")
    private Integer arrWindGustKt;

    @Column(name = "arr_visibility_m")
    private Integer arrVisibilityM;

    @Column(name = "arr_ceiling_ft")
    private Integer arrCeilingFt;

    @Column(name = "arr_cavok")
    private Boolean arrCavok;

    @Column(name = "arr_conditions")
    private String arrConditions;

    @Column(name = "arr_flight_category")
    private String arrFlightCategory;

    @Column(name = "arr_wx_raw")
    private String arrWxRaw;

    @Column(name = "out_at")
    private OffsetDateTime outAt;

    @Column(name = "dep_delay_min")
    private Integer depDelayMin;

    @Column(name = "target_delay15")
    private Boolean targetDelay15;

    @Column(name = "delay_code")
    private String delayCode;

    @Column(name = "cancelled", nullable = false)
    private boolean cancelled;

    @Column(name = "outcome_at")
    private OffsetDateTime outcomeAt;
}
