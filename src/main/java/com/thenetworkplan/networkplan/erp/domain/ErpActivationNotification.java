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

/** One statutory notification, made. */
@Entity
@Table(name = "erp_activation_notifications", schema = "safety")
@Getter
@Setter
public class ErpActivationNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "made_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime madeAt = OffsetDateTime.now();

    @Column(name = "made_by", nullable = false)
    private String madeBy;

    /** How, and to whom. The evidence of the notification, not the notification. */
    @Column(name = "channel")
    private String channel;

    @Column(name = "reference")
    private String reference;
}
