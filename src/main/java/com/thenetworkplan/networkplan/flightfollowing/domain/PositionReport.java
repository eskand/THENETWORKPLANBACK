package com.thenetworkplan.networkplan.flightfollowing.domain;

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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One position, as received.
 *
 * <p>{@code reportedAt} is when the aircraft was there; {@code receivedAt} is
 * when we learnt it. The difference is the latency an OCC needs to judge how
 * much to trust the dot, and the prototype had neither.
 */
@Entity
@Table(name = "position_reports", schema = "ops")
@Getter
@Setter
public class PositionReport extends BaseEntity {

    /** Plain identifier: a position can exist without a leg (a ferry, a test). */
    @Column(name = "leg_id")
    private UUID legId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "reported_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime reportedAt;

    @Column(name = "received_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Column(name = "altitude_ft")
    private Integer altitudeFt;

    @Column(name = "ground_speed_kt")
    private Integer groundSpeedKt;

    @Column(name = "track_deg")
    private Integer trackDeg;

    @Column(name = "vertical_rate_fpm")
    private Integer verticalRateFpm;

    @Column(name = "on_ground")
    private Boolean onGround;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PositionProvider provider;

    @Column(name = "provider_ref")
    private String providerRef;
}
