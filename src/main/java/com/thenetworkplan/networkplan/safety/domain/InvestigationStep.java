package com.thenetworkplan.networkplan.safety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One link of the five-why chain.
 *
 * <p>Append-only in practice and deliberately narrow: an ordered statement and
 * nothing else. ICAO Doc 9859 asks for the analysis to be traceable link by
 * link, which a single text column is not — the first statement containing the
 * separator would silently split the chain in two.
 */
@Entity
@Table(name = "investigation_steps", schema = "safety")
@Getter
@Setter
public class InvestigationStep {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    /** 1 is what happened; the last is the condition that allowed it. */
    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "statement", nullable = false)
    private String statement;
}
