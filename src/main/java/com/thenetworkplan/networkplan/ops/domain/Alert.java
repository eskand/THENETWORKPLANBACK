package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A ranked preparation alert with an explicit cause and an acknowledgement.
 *
 * <p>The product is supposed to carry the watch instead of the OCC: nothing
 * important should be discovered by looking. An alert therefore names the rule
 * that raised it, the cause in plain words, and who it is for.
 */
@Entity
@Table(name = "alerts", schema = "ops")
@Getter
@Setter
public class Alert extends BaseEntity {

    /** Null for a fleet-level alert such as an AOG. */
    @Column(name = "leg_id")
    private UUID legId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    @Column(name = "rule", nullable = false)
    private String rule;

    @Column(name = "cause", nullable = false)
    private String cause;

    @Column(name = "target_role")
    private String targetRole;

    @Column(name = "acked_by")
    private UUID ackedBy;

    @Column(name = "acked_at", columnDefinition = "timestamptz")
    private OffsetDateTime ackedAt;

    @Column(name = "escalated_at", columnDefinition = "timestamptz")
    private OffsetDateTime escalatedAt;

    public boolean open() {
        return ackedAt == null;
    }
}
