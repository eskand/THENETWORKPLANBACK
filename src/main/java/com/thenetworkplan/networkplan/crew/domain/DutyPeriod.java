package com.thenetworkplan.networkplan.crew.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One duty period of one crew member: the only base of every FTL counter.
 *
 * <p>The audit found four concurrent duty computations in the prototype. There
 * is one table here and one way to add it up; a screen that shows a cumulative
 * figure reads a {@code sum} over this table and nothing else.
 *
 * <p>{@code legId} is a plain identifier, not an association: DOM4 does not join
 * DOM1 (annexe A2), and the roster is meaningful even for a duty with no leg.
 */
@Entity
@Table(name = "duty_periods", schema = "crew")
@Getter
@Setter
public class DutyPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "leg_id")
    private UUID legId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private DutyKind kind;

    @Column(name = "report_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime reportAt;

    @Column(name = "off_duty_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime offDutyAt;

    /** Block-to-block flight time. Null when the period carries no flying. */
    @Column(name = "block_minutes")
    private Integer blockMinutes;

    @Column(name = "sectors", nullable = false)
    private int sectors;

    @Column(name = "remark")
    private String remark;

    /** Elapsed duty, in minutes, from reporting to off duty. */
    public long dutyMinutes() {
        return Duration.between(reportAt, offDutyAt).toMinutes();
    }
}
