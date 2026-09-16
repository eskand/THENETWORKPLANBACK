package com.thenetworkplan.networkplan.tripsupport.repository;

import com.thenetworkplan.networkplan.tripsupport.domain.PermitRequest;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermitRequestRepository extends JpaRepository<PermitRequest, UUID> {

    @Query("""
            select new com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount(
                       p.legId, p.status, count(p))
            from PermitRequest p
            where p.tenantId = :tenantId
              and p.legId in :legIds
            group by p.legId, p.status
            """)
    List<LegRequestCount> countByLegAndStatus(@Param("tenantId") UUID tenantId,
                                              @Param("legIds") Collection<UUID> legIds);

    /** Permits still not granted across the whole tenant: the KPI tile. */
    @Query("""
            select count(p) from PermitRequest p
            where p.tenantId = :tenantId
              and p.status <> :confirmed
              and p.legId in :legIds
            """)
    long countOutstanding(@Param("tenantId") UUID tenantId,
                          @Param("legIds") Collection<UUID> legIds,
                          @Param("confirmed") RequestStatus confirmed);

    @Query("""
            select p from PermitRequest p
            where p.tenantId = :tenantId
              and p.legId = :legId
            order by p.countryIso2, p.kind
            """)
    List<PermitRequest> findByLeg(@Param("tenantId") UUID tenantId, @Param("legId") UUID legId);

    /**
     * Permit requests not confirmed whose leg departs inside a window.
     *
     * <p>The date filter goes through the leg, because a permit is late
     * relative to the flight it covers, not to the day it was raised.
     */
    @Query("""
            select p from PermitRequest p
            where p.tenantId = :tenantId
              and p.status <> com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus.CONFIRMED
              and p.legId in (select l.id from Leg l
                              where l.tenantId = :tenantId
                                and l.std >= :from
                                and l.std < :to)
            order by p.countryIso2, p.kind
            """)
    List<PermitRequest> findOutstandingInWindow(@Param("tenantId") UUID tenantId,
                                                @Param("from") java.time.OffsetDateTime from,
                                                @Param("to") java.time.OffsetDateTime to);

    /** Every permit request of a day, in one statement, for the services board. */
    @Query("""
            select p from PermitRequest p
            where p.tenantId = :tenantId
              and p.legId in :legIds
            order by p.legId, p.countryIso2, p.kind
            """)
    List<PermitRequest> findByLegIds(@Param("tenantId") UUID tenantId,
                                     @Param("legIds") Collection<UUID> legIds);

    Optional<PermitRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
