package com.thenetworkplan.networkplan.camo.domain;

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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One maintenance task due on one registration.
 *
 * <p>The audit found the prototype fabricating a CAMO due list by hashing the
 * registration. Here a due date is written when the task is signed off — last
 * reading plus the programme interval — and read afterwards. Nothing on this
 * row is derived at display time.
 *
 * <p>All three limits can coexist: an A check is due at hours <em>or</em> at a
 * date, whichever comes first, and the rule that decides is
 * {@code MaintenanceDueRule}.
 */
@Entity
@Table(name = "aircraft_tasks", schema = "camo")
@Getter
@Setter
public class AircraftTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    /** Plain identifier: the programme belongs to the CAMO Admin module. */
    @Column(name = "programme_task_id")
    private UUID programmeTaskId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "last_done_on")
    private LocalDate lastDoneOn;

    @Column(name = "last_done_hours")
    private BigDecimal lastDoneHours;

    @Column(name = "last_done_cycles")
    private Integer lastDoneCycles;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "due_at_hours")
    private BigDecimal dueAtHours;

    @Column(name = "due_at_cycles")
    private Integer dueAtCycles;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @Column(name = "remark")
    private String remark;

    public boolean isOpen() {
        return closedAt == null;
    }
}
