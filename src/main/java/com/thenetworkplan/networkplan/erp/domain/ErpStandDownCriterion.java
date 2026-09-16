package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One condition that must hold before the plan may be stood down. */
@Entity
@Table(name = "erp_standdown_criteria", schema = "safety")
@Getter
@Setter
public class ErpStandDownCriterion extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
