package com.thenetworkplan.networkplan.tripsupport.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A supplier for one service at one station.
 *
 * <p>{@code NetPlus Services} is the operator's own desk and is seeded on every
 * station as the preferred supplier — the arrangement the prototype had, kept
 * because it is a real business rule, not an accident.
 */
@Entity
@Table(name = "suppliers", schema = "tripsupport")
@Getter
@Setter
public class Supplier extends BaseEntity {

    @Column(name = "station_icao", nullable = false)
    private String stationIcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private GroundServiceType serviceType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "sita")
    private String sita;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "contract_ref")
    private String contractRef;

    @Column(name = "preferred", nullable = false)
    private boolean preferred;

    /** Notice the supplier asks for. Feeds the "too late to request" warning. */
    @Column(name = "lead_time_hours")
    private Integer leadTimeHours;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "remark")
    private String remark;
}
