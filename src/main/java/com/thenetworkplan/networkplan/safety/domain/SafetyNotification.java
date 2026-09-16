package com.thenetworkplan.networkplan.safety.domain;

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
 * One entry in the safety notification centre.
 *
 * <p>Read state is a timestamp rather than a flag: "read" is a fact that
 * happened at a moment, and knowing when somebody saw a critical finding is
 * the question asked after the event, not whether they saw it at all.
 */
@Entity
@Table(name = "notifications", schema = "safety")
@Getter
@Setter
public class SafetyNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime at = OffsetDateTime.now();

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "severity", nullable = false)
    private String severity = "info";

    /** The reference of the thing concerned, in plain text: "OCC-2026-0113". */
    @Column(name = "entity_ref")
    private String entityRef;

    @Column(name = "domain")
    private String domain;

    @Column(name = "read_at", columnDefinition = "timestamptz")
    private OffsetDateTime readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
