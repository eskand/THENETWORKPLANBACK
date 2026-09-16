package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One finding raised by an audit.
 *
 * <p>A level 1 compromises safety and is corrected before the next flight; a
 * level 2 carries a deadline; an observation carries neither. Collapsing the
 * three into "finding" is what lets a level 1 sit in a list for a month.
 */
@Entity
@Table(name = "audit_findings", schema = "safety")
@Getter
@Setter
public class AuditFinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false)
    private Audit audit;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail")
    private String detail;

    /** The requirement breached. A finding without one is an opinion. */
    @Column(name = "requirement")
    private String requirement;

    @Column(name = "raised_on", nullable = false)
    private LocalDate raisedOn;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    public boolean isOpen() {
        return closedOn == null;
    }

    /** Past its deadline and still open. Derived, never stored. */
    public boolean isOverdue(LocalDate on) {
        return isOpen() && dueOn != null && dueOn.isBefore(on);
    }
}
