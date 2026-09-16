package com.thenetworkplan.networkplan.sales.domain;

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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** A request for quotation: where, when, how many, and whether we can do it. */
@Entity
@Table(name = "requests", schema = "sales")
@Getter
@Setter
public class SalesRequest extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "received_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "dep_icao", nullable = false)
    private String depIcao;

    @Column(name = "arr_icao", nullable = false)
    private String arrIcao;

    @Column(name = "departure_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime departureAt;

    @Column(name = "return_at", columnDefinition = "timestamptz")
    private OffsetDateTime returnAt;

    @Column(name = "pax_count", nullable = false)
    private int paxCount = 1;

    @Column(name = "flight_type", nullable = false)
    private String flightType = "PAX";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SalesRequestStatus status = SalesRequestStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "feasibility", nullable = false)
    private Feasibility feasibility = Feasibility.UNKNOWN;

    @Column(name = "feasibility_note")
    private String feasibilityNote;

    @Column(name = "remark")
    private String remark;
}
