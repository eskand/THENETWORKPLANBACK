package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Commercial mission: a client and the series of legs flown for them. */
@Entity
@Table(name = "trips", schema = "ops")
@Getter
@Setter
public class Trip extends BaseEntity {

    @Column(name = "client_ref")
    private String clientRef;

    /** Origin in DOM7 when the trip came from an accepted quote. */
    @Column(name = "sales_request_id")
    private UUID salesRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TripStatus status = TripStatus.OPEN;
}
