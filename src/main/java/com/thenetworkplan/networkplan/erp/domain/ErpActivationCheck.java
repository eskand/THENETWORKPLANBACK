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
 * One action of the plan, done.
 *
 * <p>By whom and at what time, both mandatory. A tick with no name and no hour
 * proves nothing to a board of inquiry, so the row cannot exist without them.
 *
 * <p>Not a {@code BaseEntity}: {@code doneAt} <em>is</em> the creation time, and
 * a second timestamp beside it would eventually be the one someone read.
 */
@Entity
@Table(name = "erp_activation_checks", schema = "safety")
@Getter
@Setter
public class ErpActivationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "done_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime doneAt = OffsetDateTime.now();

    @Column(name = "done_by", nullable = false)
    private String doneBy;
}
