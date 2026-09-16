package com.thenetworkplan.networkplan.camoadmin.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
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
import lombok.Getter;
import lombok.Setter;

/** One directive against one registration. */
@Entity
@Table(name = "directive_applications", schema = "camo")
@Getter
@Setter
public class DirectiveApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directive_id", nullable = false)
    private Directive directive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DirectiveStatus status = DirectiveStatus.OPEN;

    @Column(name = "complied_on")
    private LocalDate compliedOn;

    @Column(name = "complied_ref")
    private String compliedRef;

    @Column(name = "remark")
    private String remark;
}
