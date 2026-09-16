package com.thenetworkplan.networkplan.sales.domain;

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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A priced answer to a request.
 *
 * <p>{@code currency} is the currency of the quote itself; each line carries
 * its own currency and the rate used to convert it. A total is therefore a sum
 * of converted amounts — the audit found currencies added together as if they
 * were the same.
 */
@Entity
@Table(name = "quotes", schema = "sales")
@Getter
@Setter
public class Quote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private SalesRequest request;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "currency", nullable = false)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "decided_at", columnDefinition = "timestamptz")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    /** The trip created when the quote is accepted. Plain identifier: DOM1. */
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "remark")
    private String remark;
}
