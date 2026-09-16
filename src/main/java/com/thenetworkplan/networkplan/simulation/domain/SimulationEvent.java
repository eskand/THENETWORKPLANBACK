package com.thenetworkplan.networkplan.simulation.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One inject of a scenario, at a stated offset from its start. */
@Entity
@Table(name = "simulation_events", schema = "planning")
@Getter
@Setter
public class SimulationEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private SimulationScenario scenario;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Column(name = "kind", nullable = false)
    private String kind;

    /** By value, never by foreign key — see the note on the scenario. */
    @Column(name = "registration")
    private String registration;

    @Column(name = "flight_no")
    private String flightNo;

    @Column(name = "station_icao")
    private String stationIcao;

    @Column(name = "detail", nullable = false)
    private String detail;

    @Column(name = "expected_action")
    private String expectedAction;
}
