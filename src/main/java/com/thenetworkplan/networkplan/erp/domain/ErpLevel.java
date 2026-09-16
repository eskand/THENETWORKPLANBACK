package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One of the five levels of the plan, in the operator's own words.
 *
 * <p>The level colours the whole console, so it is stored rather than named in
 * three places: the console, the crisis log and any printed record all show it
 * and would eventually disagree about what a level 3 is.
 */
@Entity
@Table(name = "erp_levels", schema = "safety")
@Getter
@Setter
public class ErpLevel extends BaseEntity {

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "colour", nullable = false)
    private String colour;

    @Column(name = "description", nullable = false)
    private String description;

    /** What activating at this level actually starts. */
    @Column(name = "activation", nullable = false)
    private String activation;

    /** Who stands up. Printed as written: at 3 a.m. nobody paraphrases. */
    @Column(name = "stands_up", nullable = false)
    private String standsUp;
}
