package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One audit of the safety programme.
 *
 * <p>Component three of ICAO Annex 19 — safety assurance. Without it the
 * dashboard's "open audit findings" has nothing behind it, and the operator
 * has no evidence that its own management system is being checked.
 */
@Entity
@Table(name = "audits", schema = "safety")
@Getter
@Setter
public class Audit extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "name", nullable = false)
    private String name;

    /** IS-BAO, EASA ORO.GEN.200, ICAO Annex 19, Part-CAMO, Internal. */
    @Column(name = "standard", nullable = false)
    private String standard;

    @Column(name = "scope")
    private String scope;

    @Column(name = "auditor")
    private String auditor;

    /** An external audit is not closed by the operator that was audited. */
    @Column(name = "external_audit", nullable = false)
    private boolean externalAudit;

    @Column(name = "planned_on", nullable = false)
    private LocalDate plannedOn;

    @Column(name = "conducted_on")
    private LocalDate conductedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    /** Null until the audit happens: zero would read as total non-compliance. */
    @Column(name = "score_percent")
    private Short scorePercent;

    @Column(name = "remark")
    private String remark;
}
