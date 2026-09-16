package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One activation of the plan: an exercise, a standby, or the real thing.
 *
 * <p>An exercise is stored exactly like a real activation and is distinguished
 * by {@code kind} alone — that is what lets the operator show an authority how
 * often the plan is exercised, from the same table.
 */
@Entity
@Table(name = "erp_activations", schema = "safety")
@Getter
@Setter
public class ErpActivation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ErpPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ActivationKind kind;

    @Column(name = "reference", nullable = false)
    private String reference;

    /** Plain identifier: DOM6 does not join DOM1. */
    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "activated_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime activatedAt = OffsetDateTime.now();

    @Column(name = "activated_by")
    private UUID activatedBy;

    @Column(name = "stood_down_at", columnDefinition = "timestamptz")
    private OffsetDateTime stoodDownAt;

    @Column(name = "situation")
    private String situation;

    /**
     * 0 to 4, and the whole screen is coloured by it.
     *
     * <p>A level 1 is handled from the OCC desk; a level 4 opens a family
     * assistance centre. Without it, "activated" does not say what was
     * activated, and the console cannot tell a standby from an accident.
     */
    @Column(name = "level", nullable = false)
    private short level;

    /** One line naming the event: what the crisis is about. */
    @Column(name = "event_label")
    private String eventLabel;

    /* --- authorisation ---
       The plan requires the OCC Manager and the Safety Manager acting
       together. Two names, kept on the record, because an emergency plan
       triggered by one person alone is how a drill becomes an incident. */

    @Column(name = "initiated_by_name")
    private String initiatedByName;

    @Column(name = "initiated_by_role")
    private String initiatedByRole;

    @Column(name = "concurred_by_name")
    private String concurredByName;

    @Column(name = "concurred_by_role")
    private String concurredByRole;

    /**
     * The Accountable Manager's override, when activating alone.
     *
     * <p>It carries a reason or it is not an override. The database refuses a
     * real activation that has neither a second name nor a reason.
     */
    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "stood_down_by")
    private String stoodDownBy;

    /** The SMS occurrence this event is also recorded as, when there is one. */
    @Column(name = "sms_occurrence_id")
    private java.util.UUID smsOccurrenceId;

    /** The case from the emergency catalogue, when the assessment named one. */
    @Column(name = "event_code")
    private String eventCode;

    /* --- the aircraft the crisis is about ---
       Eleven columns rather than one JSON blob: each is read on its own by
       the console, and an empty one has to be visible as empty. */

    @Column(name = "flight")
    private String flight;

    @Column(name = "registration")
    private String registration;

    @Column(name = "aircraft_type")
    private String aircraftType;

    @Column(name = "origin")
    private String origin;

    @Column(name = "destination")
    private String destination;

    @Column(name = "pob")
    private String pob;

    @Column(name = "dangerous_goods")
    private String dangerousGoods;

    @Column(name = "last_position")
    private String lastPosition;

    @Column(name = "squawk")
    private String squawk;

    @Column(name = "fuel_state")
    private String fuelState;

    @Column(name = "souls")
    private String souls;

    /** Whether anything has yet been said in public. */
    @Column(name = "comms_issued", nullable = false)
    private boolean commsIssued;

    /** Still running. */
    public boolean isOpen() {
        return stoodDownAt == null;
    }
}
