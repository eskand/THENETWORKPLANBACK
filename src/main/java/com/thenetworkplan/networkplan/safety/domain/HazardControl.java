package com.thenetworkplan.networkplan.safety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One barrier against a hazard.
 *
 * <p>Preventive stops the event; recovery limits what happens once it has
 * started. They are not worth the same thing, and a register that does not
 * distinguish them cannot show whether an operator is preventing anything or
 * merely catching the pieces.
 */
@Entity
@Table(name = "hazard_controls", schema = "safety")
@Getter
@Setter
public class HazardControl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "hazard_id", nullable = false)
    private UUID hazardId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "control_type", nullable = false)
    private String controlType = "preventive";

    @Column(name = "owner")
    private String owner;

    /** planned, in-place or withdrawn. A planned control is not a control yet. */
    @Column(name = "status", nullable = false)
    private String status = "planned";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
