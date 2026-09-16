package com.thenetworkplan.networkplan.camoadmin.domain;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * An airworthiness directive or a service bulletin, as published.
 *
 * <p>Published by an authority or a manufacturer, so it carries no per-tail
 * state: what each registration has done with it lives in
 * {@link DirectiveApplication}. One row here, one row per aircraft there.
 */
@Entity
@Table(name = "directives", schema = "camo")
@Getter
@Setter
public class Directive extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private DirectiveKind kind;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "effective_on")
    private LocalDate effectiveOn;

    /** Null when the directive applies to every type in the fleet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @Column(name = "compliance_by_date")
    private LocalDate complianceByDate;

    @Column(name = "compliance_by_hours")
    private BigDecimal complianceByHours;

    @Column(name = "method")
    private String method;

    @Column(name = "recurring_months")
    private Integer recurringMonths;
}
