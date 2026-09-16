package com.thenetworkplan.networkplan.simulation.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A training scenario.
 *
 * <p>The audit found a Simulation Center whose random stub wrote onto the live
 * timeline. This aggregate lives in the {@code planning} schema and holds
 * <em>no</em> foreign key into {@code ops}: a scenario cannot, by
 * construction, touch the real programme. Aircraft and flights are designated
 * by their text — a registration, a flight number — precisely so that no
 * association exists to follow.
 */
@Entity
@Table(name = "simulation_scenarios", schema = "planning")
@Getter
@Setter
public class SimulationScenario extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "narrative")
    private String narrative;

    /** The day the exercise is played against. A date, not a link to a leg. */
    @Column(name = "baseline_date", nullable = false)
    private LocalDate baselineDate;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "last_run_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_run_by")
    private UUID lastRunBy;
}
