package com.thenetworkplan.networkplan.vigil.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Une alerte VIGIL — l'objet que {@code ALERTS.upsert()} de l'annexe
 * (l. 98695) posait dans localStorage, avec les memes champs.
 *
 * <p>{@code signature} est son identite : la meme regle sur le meme vol le
 * meme jour est la meme alerte, mise a jour a chaque balayage et non
 * dupliquee. C'est ce qui permet d'accuser reception une fois et que
 * l'accuse tienne.
 */
@Entity
@Table(name = "vigil_alerts", schema = "ops")
@Getter
@Setter
public class VigilAlert extends BaseEntity {

    @Column(name = "signature", nullable = false, updatable = false)
    private String signature;

    @Column(name = "rule", nullable = false)
    private String rule;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "flight_no")
    private String flightNo;

    @Column(name = "registration")
    private String registration;

    @Column(name = "flight_date", nullable = false)
    private LocalDate flightDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private VigilSeverity severity;

    @Column(name = "risk")
    private Integer risk;

    /** rule, fleet, dq… — d'ou vient le constat. */
    @Column(name = "method", nullable = false)
    private String method = "rule";

    @Column(name = "why", nullable = false)
    private String why;

    @Column(name = "impact")
    private String impact;

    @Column(name = "action")
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VigilAlertStatus status = VigilAlertStatus.OPEN;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime lastSeenAt;

    @Column(name = "reopened_at", columnDefinition = "timestamptz")
    private OffsetDateTime reopenedAt;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
