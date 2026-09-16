package com.thenetworkplan.networkplan.camo.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * One airworthiness review certificate.
 *
 * <p>This is the row that says whether a registration may fly at all. A check
 * that is due grounds an aircraft for a few days; an expired ARC grounds it
 * until an authority acts, which is a different order of problem, and the
 * screen had no way to tell them apart because nothing held the certificate.
 *
 * <p><b>Renewal writes a new row.</b> The previous certificate is not
 * overwritten: it is stamped {@code supersededAt} and kept. A partial unique
 * index enforces that only one row per aircraft is unstamped, so "the
 * certificate in force" is a fact of the database and not a convention the
 * service has to remember. The same discipline as published roster versions —
 * what was certified on a date must stay readable as it was.
 *
 * <p><b>Days remaining are not stored.</b> The prototype froze them at
 * authoring time and they drifted fourteen days out before its own code had to
 * recompute them on top. Here the expiry date is stored and the remainder is
 * asked for, which cannot go stale.
 */
@Entity
@Table(name = "airworthiness_reviews", schema = "camo")
@Getter
@Setter
public class AirworthinessReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "certificate_no", nullable = false)
    private String certificateNo;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_basis", nullable = false)
    private ReviewBasis reviewBasis = ReviewBasis.FULL;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    /** The reviewer's own approval number — a signature without one proves nothing. */
    @Column(name = "reviewer_approval_no")
    private String reviewerApprovalNo;

    /** The certificate of airworthiness this review attests remains valid. */
    @Column(name = "cofa_ref")
    private String cofaRef;

    /** Null while this is the certificate in force. */
    @Column(name = "superseded_at", columnDefinition = "timestamptz")
    private OffsetDateTime supersededAt;

    @Column(name = "remark")
    private String remark;

    /** The one certificate that answers for the aircraft today. */
    public boolean isInForce() {
        return supersededAt == null;
    }

    /**
     * Days from {@code on} to expiry; negative once expired.
     *
     * <p>Returned rather than stored, so it cannot be right on the day it is
     * written and wrong every day after.
     */
    public long daysLeft(LocalDate on) {
        return ChronoUnit.DAYS.between(on, expiresOn);
    }
}
