package com.thenetworkplan.networkplan.camo.repository;

import com.thenetworkplan.networkplan.camo.domain.AirworthinessReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AirworthinessReviewRepository extends JpaRepository<AirworthinessReview, UUID> {

    /**
     * The certificate in force for every registration of the fleet.
     *
     * <p>One row per aircraft at most — the partial unique index in V30
     * guarantees it, so the caller can key by aircraft without deduplicating.
     * An aircraft with no certificate simply has no row here, and that absence
     * is itself the answer the screen needs.
     */
    @Query("""
            select r from AirworthinessReview r
            join fetch r.aircraft a
            where r.tenantId = :tenantId
              and r.supersededAt is null
            order by r.expiresOn
            """)
    List<AirworthinessReview> findInForce(@Param("tenantId") UUID tenantId);

    /**
     * Every certificate ever issued to one registration, newest first.
     *
     * <p>The one in force leads; the superseded ones follow in the order they
     * were replaced. The list is the aircraft's continuity of airworthiness,
     * and a gap between an expiry and the next issue is visible in it.
     */
    @Query("""
            select r from AirworthinessReview r
            join fetch r.aircraft a
            where r.tenantId = :tenantId
              and a.id = :aircraftId
            order by r.issuedOn desc
            """)
    List<AirworthinessReview> findByAircraft(@Param("tenantId") UUID tenantId,
                                             @Param("aircraftId") UUID aircraftId);
}
