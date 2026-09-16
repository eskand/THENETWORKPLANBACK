package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A signed dispatch release, and the commander's acknowledgement of it.
 *
 * <p>{@link #blockingChecks} freezes the readiness result as it stood at
 * signature. That is the difference between a release and a checkbox: months
 * later, an authority can see what the dispatcher was looking at.
 */
@Entity
@Table(name = "releases", schema = "ops")
@Getter
@Setter
public class Release extends BaseEntity {

    @Column(name = "leg_id", nullable = false, updatable = false)
    private UUID legId;

    /** Increments on a re-release. */
    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "signed_by")
    private UUID signedBy;

    @Column(name = "signed_at", columnDefinition = "timestamptz")
    private OffsetDateTime signedAt;

    /** Release granted despite a derogable finding; the reason is then mandatory. */
    @Column(name = "derogation", nullable = false)
    private boolean derogation;

    @Column(name = "derogation_reason")
    private String derogationReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocking_checks", columnDefinition = "jsonb")
    private String blockingChecks;

    @Column(name = "dossier_document_id")
    private UUID dossierDocumentId;

    @Column(name = "captain_ack_by")
    private UUID captainAckBy;

    @Column(name = "captain_ack_at", columnDefinition = "timestamptz")
    private OffsetDateTime captainAckAt;

    public boolean acknowledged() {
        return captainAckAt != null;
    }
}
