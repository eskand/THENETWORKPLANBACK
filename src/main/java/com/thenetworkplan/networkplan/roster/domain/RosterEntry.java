package com.thenetworkplan.networkplan.roster.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.crew.domain.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One cell of the grid: one person, one day, one code.
 *
 * <p>{@code dutyPeriodId} and {@code legId} are plain identifiers. A cell that
 * carries a duty period is the display of a fact recorded in DOM4; a cell
 * without one is a plan and nothing more, and the screen says which is which.
 */
@Entity
@Table(name = "roster_entries", schema = "crew")
@Getter
@Setter
public class RosterEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roster_version_id", nullable = false)
    private RosterVersion rosterVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false)
    private RosterCode code;

    @Column(name = "duty_period_id")
    private UUID dutyPeriodId;

    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "remark")
    private String remark;
}
