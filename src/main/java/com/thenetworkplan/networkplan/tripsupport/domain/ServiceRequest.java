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

/** A ground-service request at one station for one leg. */
@Entity
@Table(name = "service_requests", schema = "tripsupport")
@Getter
@Setter
public class ServiceRequest extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Column(name = "station_icao", nullable = false)
    private String stationIcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private GroundServiceType serviceType;

    @Column(name = "supplier_name")
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(name = "reference")
    private String reference;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "acknowledged_at", columnDefinition = "timestamptz")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime confirmedAt;

    @Column(name = "remark")
    private String remark;
}
