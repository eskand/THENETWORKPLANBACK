package com.thenetworkplan.networkplan.refdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One question of the severity assessment. */
@Entity
@Table(name = "erp_questions", schema = "refdata")
@Getter
@Setter
public class ErpQuestion {

    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
