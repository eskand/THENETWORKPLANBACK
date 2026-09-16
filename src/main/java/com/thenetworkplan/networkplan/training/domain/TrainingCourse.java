package com.thenetworkplan.networkplan.training.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A course of the training programme.
 *
 * <p>Lives in the {@code crew} schema: training is part of DOM4, and A2 gives
 * one schema per domain, not one per module. The module owns these tables and
 * is the only code that writes them.
 *
 * <p>{@code validityMonths} is the operator's programme, not a constant in the
 * code: an authority that shortens a recurrent cycle changes a row, not a
 * release.
 */
@Entity
@Table(name = "training_courses", schema = "crew")
@Getter
@Setter
public class TrainingCourse extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TrainingCategory category;

    /** Null when the course never expires (an initial course, typically). */
    @Column(name = "validity_months")
    private Integer validityMonths;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "authority_ref")
    private String authorityRef;
}
