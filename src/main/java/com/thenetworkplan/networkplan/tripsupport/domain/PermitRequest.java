package com.thenetworkplan.networkplan.tripsupport.domain;

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

/** An overflight or landing permit request and its answer. */
@Entity
@Table(name = "permit_requests", schema = "tripsupport")
@Getter
@Setter
public class PermitRequest extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Column(name = "country_iso2", nullable = false)
    private String countryIso2;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private PermitKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "reference")
    private String reference;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "sent_by")
    private UUID sentBy;

    @Column(name = "acknowledged_at", columnDefinition = "timestamptz")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime confirmedAt;

    @Column(name = "valid_from", columnDefinition = "timestamptz")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "timestamptz")
    private OffsetDateTime validTo;

    @Column(name = "message_document_id")
    private UUID messageDocumentId;
}
