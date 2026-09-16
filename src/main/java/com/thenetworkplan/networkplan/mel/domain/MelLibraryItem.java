package com.thenetworkplan.networkplan.mel.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.MelCategory;
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
import lombok.Getter;
import lombok.Setter;

/**
 * One line of the operator MEL.
 *
 * <p>This table is why a deferred defect can have a due date at all: the audit
 * found {@code MEL dueDate '—'} because the prototype had nowhere to read the
 * rectification interval from. Category and interval live here, once, and every
 * deferral reads them.
 */
@Entity
@Table(name = "mel_library", schema = "camo")
@Getter
@Setter
public class MelLibraryItem extends BaseEntity {

    /** Null for a line that applies to the whole fleet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @Column(name = "item_ref", nullable = false)
    private String itemRef;

    @Column(name = "ata_chapter", nullable = false)
    private String ataChapter;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "mel_category", nullable = false)
    private MelCategory melCategory;

    /** Part-MEL interval in days. Null on a category A line judged case by case. */
    @Column(name = "rectification_days")
    private Integer rectificationDays;

    @Column(name = "installed_quantity")
    private Integer installedQuantity;

    @Column(name = "required_quantity")
    private Integer requiredQuantity;

    @Column(name = "placard_required", nullable = false)
    private boolean placardRequired;

    @Column(name = "operational_procedure")
    private String operationalProcedure;

    @Column(name = "maintenance_procedure")
    private String maintenanceProcedure;

    @Column(name = "limitation")
    private String limitation;
}
