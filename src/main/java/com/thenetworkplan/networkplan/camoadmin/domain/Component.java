package com.thenetworkplan.networkplan.camoadmin.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One component of the fleet: an engine, an APU, a landing gear, or anything
 * else with a part number and a serial number.
 *
 * <p>The prototype keeps four collections with the same fields. They are four
 * <em>views</em> of one thing — an engine is a component with a position — and
 * four tables would eventually disagree about what a TSO counts from.
 *
 * <p><b>Life remaining is not stored.</b> It is the limit minus the hours as at
 * the moment of reading. A stored figure is right on the day it is written and
 * wrong on every day after.
 */
@Entity
@Table(name = "components", schema = "camo")
@Getter
@Setter
public class Component extends BaseEntity {

    @Column(name = "category", nullable = false)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_id")
    private Aircraft aircraft;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ata_chapter")
    private String ataChapter;

    @Column(name = "part_number")
    private String partNumber;

    @Column(name = "serial_number")
    private String serialNumber;

    /** "Engine 1", "MLG-L". Empty for a component with no fixed position. */
    @Column(name = "position")
    private String position;

    @Column(name = "install_date")
    private LocalDate installDate;

    @Column(name = "install_hours")
    private BigDecimal installHours;

    @Column(name = "install_cycles")
    private Integer installCycles;

    @Column(name = "removal_date")
    private LocalDate removalDate;

    @Column(name = "tsn")
    private BigDecimal tsn;

    @Column(name = "csn")
    private Integer csn;

    @Column(name = "tso")
    private BigDecimal tso;

    @Column(name = "cso")
    private Integer cso;

    @Column(name = "life_limit_hours")
    private BigDecimal lifeLimitHours;

    @Column(name = "life_limit_cycles")
    private Integer lifeLimitCycles;

    /** Some parts come off on a date, whatever the hours say. */
    @Column(name = "calendar_limit")
    private LocalDate calendarLimit;

    @Column(name = "overhaul_due")
    private LocalDate overhaulDue;

    @Column(name = "overhaul_due_hours")
    private BigDecimal overhaulDueHours;

    @Column(name = "next_inspection")
    private LocalDate nextInspection;

    @Column(name = "next_inspection_hours")
    private BigDecimal nextInspectionHours;

    /** The two trends a CAMO actually watches on an engine. */
    @Column(name = "egt_margin")
    private BigDecimal egtMargin;

    @Column(name = "oil_consumption")
    private BigDecimal oilConsumption;

    @Column(name = "status", nullable = false)
    private String status = "INSTALLED";

    @Column(name = "notes")
    private String notes;
}
