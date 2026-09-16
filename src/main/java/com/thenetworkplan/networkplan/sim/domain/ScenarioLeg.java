package com.thenetworkplan.networkplan.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One leg inside a scenario. A copy of a real one, or one an injector invented.
 *
 * <p>{@code sourceLegId} carries the identifier of the leg it was copied from
 * and is deliberately <b>not</b> a foreign key: a snapshot has to survive the
 * disappearance of its source, and one that changed when the source changed
 * would not be a snapshot.
 */
@Entity
@Table(name = "scenario_legs", schema = "sim")
@Getter
@Setter
public class ScenarioLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "source_leg_id")
    private UUID sourceLegId;

    @Column(name = "registration", nullable = false)
    private String registration;

    @Column(name = "icao_type")
    private String icaoType;

    @Column(name = "flight_no", nullable = false)
    private String flightNo;

    @Column(name = "dep_icao", nullable = false)
    private String depIcao;

    @Column(name = "arr_icao", nullable = false)
    private String arrIcao;

    @Column(name = "std", nullable = false)
    private OffsetDateTime std;

    @Column(name = "sta", nullable = false)
    private OffsetDateTime sta;

    @Column(name = "flight_type", nullable = false)
    private String flightType = "PAX";

    @Column(name = "pax_count", nullable = false)
    private int paxCount;

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    /** Where the shift in std and sta came from. The times already carry it. */
    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    /** True when an injector created this leg rather than copying one. */
    @Column(name = "injected", nullable = false)
    private boolean injected;

    /** Block time in minutes, as planned in this scenario. */
    public long blockMinutes() {
        return java.time.Duration.between(std, sta).toMinutes();
    }
}
