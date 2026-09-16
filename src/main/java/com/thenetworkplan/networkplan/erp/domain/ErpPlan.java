package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * The emergency response plan, at one revision.
 *
 * <p>{@code reviewDueOn} is what makes the plan a living document: a plan
 * whose review is overdue is shown as such, because an ERP nobody has read
 * for three years is not a plan.
 */
@Entity
@Table(name = "erp_plans", schema = "safety")
@Getter
@Setter
public class ErpPlan extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "revision", nullable = false)
    private String revision;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "review_due_on")
    private LocalDate reviewDueOn;

    @Column(name = "summary")
    private String summary;
}
