package com.thenetworkplan.networkplan.crew.domain;

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
 * One person in one seat on one leg, with the FTL verdict recorded at assignment
 * time and the reason behind it.
 *
 * <p>{@code legId} is a plain identifier, not an association: the crew module
 * references the leg but never joins across the domain boundary (annexe A2). The
 * foreign key exists in the database for integrity; the read path goes through
 * DOM1's API.
 */
@Entity
@Table(name = "leg_assignments", schema = "crew")
@Getter
@Setter
public class CrewAssignment extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat", nullable = false)
    private CrewSeat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "ftl_verdict", nullable = false)
    private FtlVerdict ftlVerdict = FtlVerdict.UNKNOWN;

    @Column(name = "ftl_reason")
    private String ftlReason;

    @Column(name = "duty_start", columnDefinition = "timestamptz")
    private OffsetDateTime dutyStart;

    @Column(name = "duty_end", columnDefinition = "timestamptz")
    private OffsetDateTime dutyEnd;

    @Column(name = "checked_in_at", columnDefinition = "timestamptz")
    private OffsetDateTime checkedInAt;

    @Column(name = "checked_out_at", columnDefinition = "timestamptz")
    private OffsetDateTime checkedOutAt;
}
