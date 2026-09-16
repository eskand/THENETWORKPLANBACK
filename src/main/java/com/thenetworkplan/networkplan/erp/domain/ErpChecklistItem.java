package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One action of the plan, owned by a cell.
 *
 * <p>Phase 0 is the universal first response: twelve items a duty officer can
 * complete without deciding anything. Everything needing judgement is phase 1
 * and carries the minimum level at which it applies.
 */
@Entity
@Table(name = "erp_checklist_items", schema = "safety")
@Getter
@Setter
public class ErpChecklistItem extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "phase", nullable = false)
    private short phase;

    @Column(name = "dept_code", nullable = false)
    private String deptCode;

    /** Null in phase 0: phase 0 always applies. */
    @Column(name = "min_level")
    private Short minLevel;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean isPhaseZero() {
        return phase == 0;
    }

    /** Whether this action is in force at the level the crisis is running at. */
    public boolean appliesAt(short level) {
        return minLevel == null || level >= minLevel;
    }
}
