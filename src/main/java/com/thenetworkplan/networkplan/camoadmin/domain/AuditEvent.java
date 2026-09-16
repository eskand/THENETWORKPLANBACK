package com.thenetworkplan.networkplan.camoadmin.domain;

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
 * One change to the airworthiness record.
 *
 * <p><b>The previous value is what makes this an audit trail.</b> Without it
 * the table is a list of events: it can say that someone touched the ARC expiry
 * on Tuesday, but not what it used to say — which is the only question ever
 * asked of it.
 *
 * <p>Append-only, and not a {@code BaseEntity}: {@code at} is the creation
 * time, and an audit row that could be updated would not be an audit row.
 */
@Entity
@Table(name = "audit_events", schema = "platform")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime at = OffsetDateTime.now();

    @Column(name = "entity", nullable = false)
    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    /** What was on the screen: "TS-NPA", not a uuid. Readable six months later. */
    @Column(name = "entity_label")
    private String entityLabel;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "field")
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "reason")
    private String reason;
}
