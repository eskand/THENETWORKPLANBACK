package com.thenetworkplan.networkplan.permits.repository;

import com.thenetworkplan.networkplan.permits.domain.NavChargeZone;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NavChargeZoneRepository extends JpaRepository<NavChargeZone, UUID> {

    /**
     * The rate of one charging zone.
     *
     * <p>Reference data read on every analysis, so it is cached: the whole
     * table is forty-one rows that change once a month.
     */
    @Query("""
            select z from NavChargeZone z
            where z.provider = :provider and z.zoneCode = :zone
            order by z.effectiveFrom desc
            limit 1
            """)
    Optional<NavChargeZone> findRate(@Param("provider") String provider,
                                     @Param("zone") String zone);

    /** A documented FIR-to-zone exception, when one exists. */
    @Query(value = "select zone_code from refdata.fir_charge_zone_overrides where fir_code = :fir",
           nativeQuery = true)
    Optional<String> findZoneOverride(@Param("fir") String firCode);

    /** True when this FIR is billed by that provider's flat scheme. */
    @Query(value = """
            select exists (select 1 from refdata.provider_firs
                           where provider = :provider and fir_code = :fir)
            """, nativeQuery = true)
    boolean isProviderFir(@Param("provider") String provider, @Param("fir") String firCode);
}
