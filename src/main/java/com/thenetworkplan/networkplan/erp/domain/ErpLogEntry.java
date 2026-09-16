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

/**
 * One line of the crisis log.
 *
 * <p>The prototype keeps this in the browser and warns itself when it cannot:
 * <em>CRISIS LOG NOT BEING STORED</em>. A crisis log that is not stored does
 * not exist, which is why this is a table.
 */
@Entity
@Table(name = "erp_log_entries", schema = "safety")
@Getter
@Setter
public class ErpLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Null for entries recorded while the plan was armed. */
    @Column(name = "activation_id")
    private UUID activationId;

    @Column(name = "at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime at = OffsetDateTime.now();

    /** activated, escalated, deescalated, sitrep, notified, check, subject, note. */
    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "actor")
    private String actor;

    @Column(name = "level")
    private Short level;
}
