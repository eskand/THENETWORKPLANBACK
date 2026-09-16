package com.thenetworkplan.networkplan.refdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One case from the emergency catalogue.
 *
 * <p>{@code baseLevel} is where the event sits <em>before</em> the assessment
 * questions are put. The assessment can raise it and can never lower it — an
 * operator should not be able to talk itself down from a MAYDAY.
 */
@Entity
@Table(name = "erp_events", schema = "refdata")
@Getter
@Setter
public class ErpEvent {

    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Column(name = "base_level", nullable = false)
    private short baseLevel;

    /** 7500, 7600, 7700 — when the transponder says it before anyone does. */
    @Column(name = "squawk")
    private String squawk;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "note", nullable = false)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
