package com.thenetworkplan.networkplan.erp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One situation report: what was known, when, and who said it. */
@Entity
@Table(name = "erp_sitreps", schema = "safety")
@Getter
@Setter
public class ErpSitrep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime at = OffsetDateTime.now();

    /** The level at the moment of writing, so a later escalation cannot rewrite it. */
    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "author", nullable = false)
    private String author;
}
