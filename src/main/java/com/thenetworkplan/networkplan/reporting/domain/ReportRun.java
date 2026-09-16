package com.thenetworkplan.networkplan.reporting.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One execution of a report: who ran it, over what window, and how long it took. */
@Entity
@Table(name = "report_runs", schema = "platform")
@Getter
@Setter
public class ReportRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private ReportDefinition definition;

    @Column(name = "ran_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime ranAt = OffsetDateTime.now();

    @Column(name = "ran_by")
    private UUID ranBy;

    @Column(name = "window_from", nullable = false)
    private LocalDate windowFrom;

    @Column(name = "window_to", nullable = false)
    private LocalDate windowTo;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "duration_ms")
    private Integer durationMs;
}
