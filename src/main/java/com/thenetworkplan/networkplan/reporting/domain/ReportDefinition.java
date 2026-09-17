package com.thenetworkplan.networkplan.reporting.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A report is a question, not a result.
 *
 * <p>The definition is stored; the answer is recomputed on every run and
 * stamped with the instant it was produced. A stored result would be a figure
 * with no date, which is what an operator cannot defend to an auditor.
 */
@Entity
@Table(name = "report_definitions", schema = "platform")
@Getter
@Setter
public class ReportDefinition extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "description")
    private String description;

    @Column(name = "module")
    private String module;

    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "scope")
    private String scope;

    @Column(name = "default_window_days", nullable = false)
    private int defaultWindowDays = 30;

    /**
     * Where the report sits in the left menu.
     *
     * <p>Neither alphabetical nor chronological: the order in which an operator
     * asks the questions. Sorting by code would open the screen on
     * « Cancelled Flights », which is the one question nobody asks on arrival.
     */
    @Column(name = "menu_order", nullable = false)
    private int menuOrder = 900;
}
