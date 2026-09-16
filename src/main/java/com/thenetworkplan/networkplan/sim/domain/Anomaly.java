package com.thenetworkplan.networkplan.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One thing the generator put wrong, and what the solver is expected to do.
 *
 * <p>{@code expectedFix} is the reason a scenario is an exercise rather than a
 * degraded plan: it is the mark scheme. Without it a trainee can be given the
 * scenario, but nobody can say afterwards whether they solved it.
 */
@Entity
@Table(name = "anomalies", schema = "sim")
@Getter
@Setter
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false)
    private AnomalyType anomalyType;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "expected_fix", nullable = false)
    private String expectedFix;

    @Column(name = "registration")
    private String registration;

    @Column(name = "day_offset")
    private Short dayOffset;

    @Column(name = "note")
    private String note;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "leg_ids", columnDefinition = "uuid[]", nullable = false)
    private UUID[] legIds = new UUID[0];
}
