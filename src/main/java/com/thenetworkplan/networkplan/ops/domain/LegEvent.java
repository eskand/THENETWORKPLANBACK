package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only history of a leg: who changed what, when and why.
 *
 * <p>This is the table the prototype could not have: its whole state lived in
 * localStorage, auto-purged past 4.2 MB, so no change was ever auditable. The
 * before and after payloads are jsonb because their shape depends on the event.
 */
@Entity
@Table(name = "leg_events", schema = "ops")
@Getter
@Setter
public class LegEvent extends BaseEntity {

    @Column(name = "leg_id", nullable = false, updatable = false)
    private UUID legId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private LegEventKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_before", columnDefinition = "jsonb", updatable = false)
    private String payloadBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_after", columnDefinition = "jsonb", updatable = false)
    private String payloadAfter;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "reason", updatable = false)
    private String reason;
}
