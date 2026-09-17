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

/** One safety recommendation arising from an investigation, in order. */
@Entity
@Table(name = "investigation_recommendations", schema = "safety")
@Getter
@Setter
public class InvestigationRecommendation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "recommendation", nullable = false)
    private String recommendation;
}
