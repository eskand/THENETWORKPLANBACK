package com.thenetworkplan.networkplan.permits.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One charging zone of one navigation charge authority, with its unit rate and
 * the period that rate applies to.
 *
 * <p>Reference data, not tenant data: a EUROCONTROL unit rate is the same for
 * every operator, so there is no {@code tenant_id} here and the entity does not
 * extend {@code BaseEntity}.
 */
@Entity
@Table(name = "nav_charge_zones", schema = "refdata")
@Getter
@Setter
public class NavChargeZone {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "zone_code", nullable = false)
    private String zoneCode;

    @Column(name = "country_label", nullable = false)
    private String countryLabel;

    /** Null when the authority bills this zone but its current rate is not on file. */
    @Column(name = "unit_rate_eur")
    private BigDecimal unitRateEur;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "source_version")
    private String sourceVersion;
}
