package com.thenetworkplan.networkplan.refdata.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A published runway.
 *
 * <p>The readiness engine compares the type's minimum runway to the longest
 * published length. Holding the runways as rows rather than a single number on
 * the airport is what will let that check become "which runway, in which
 * direction, with which declared distance" without another schema change.
 */
@Entity
@Table(name = "runways", schema = "refdata")
@Getter
@Setter
public class Runway extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airport_id", nullable = false)
    private Airport airport;

    @Column(name = "designator", nullable = false)
    private String designator;

    @Column(name = "length_ft", nullable = false)
    private int lengthFt;

    @Column(name = "width_ft")
    private Integer widthFt;

    @Column(name = "surface")
    private String surface;

    /** Landing distance available. */
    @Column(name = "lda_ft")
    private Integer ldaFt;

    /** Take-off distance available. */
    @Column(name = "toda_ft")
    private Integer todaFt;

    @Column(name = "ils_category")
    private String ilsCategory;

    @Column(name = "lighting")
    private String lighting;
}
