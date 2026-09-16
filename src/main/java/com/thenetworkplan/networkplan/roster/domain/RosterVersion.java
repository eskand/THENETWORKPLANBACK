package com.thenetworkplan.networkplan.roster.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One roster period in one state. */
@Entity
@Table(name = "roster_versions", schema = "crew")
@Getter
@Setter
public class RosterVersion extends BaseEntity {

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RosterStatus status = RosterStatus.DRAFT;

    @Column(name = "published_at", columnDefinition = "timestamptz")
    private OffsetDateTime publishedAt;

    /** Plain identifier: DOM4 does not join the platform schema. */
    @Column(name = "published_by")
    private UUID publishedBy;

    public boolean covers(LocalDate day) {
        return !day.isBefore(periodStart) && !day.isAfter(periodEnd);
    }
}
