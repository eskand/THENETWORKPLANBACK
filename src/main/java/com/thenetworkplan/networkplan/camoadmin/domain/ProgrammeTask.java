package com.thenetworkplan.networkplan.camoadmin.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One task of the maintenance programme, per aircraft type (Part-M / AMP).
 *
 * <p>This is the template; {@code camo.aircraft_tasks} holds what it becomes on
 * a given registration. Separating them is what lets the programme be amended
 * without rewriting the history of every tail.
 */
@Entity
@Table(name = "programme_tasks", schema = "camo")
@Getter
@Setter
public class ProgrammeTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_type_id", nullable = false)
    private AircraftType aircraftType;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "ata_chapter")
    private String ataChapter;

    @Column(name = "interval_hours")
    private BigDecimal intervalHours;

    @Column(name = "interval_cycles")
    private Integer intervalCycles;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    @Column(name = "tolerance_hours")
    private BigDecimal toleranceHours;

    @Column(name = "tolerance_days")
    private Integer toleranceDays;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "reference")
    private String reference;
}
