package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
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

/** A corrective or preventive action, with an owner and a due date. */
@Entity
@Table(name = "actions", schema = "safety")
@Getter
@Setter
public class SafetyAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occurrence_id")
    private Occurrence occurrence;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail")
    private String detail;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActionStatus status = ActionStatus.OPEN;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    /**
     * Whether the action worked.
     *
     * <p>{@code NOT_ASSESSED} until somebody checks: an action closed is not
     * an action that worked, and a safety management system that cannot tell
     * the two apart learns nothing.
     */
    @Column(name = "effectiveness")
    private String effectiveness;
}
