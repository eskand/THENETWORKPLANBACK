package com.thenetworkplan.networkplan.tripsupport.repository;

import com.thenetworkplan.networkplan.tripsupport.domain.GroundServiceType;
import com.thenetworkplan.networkplan.tripsupport.domain.ServiceRequest;
import com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    /**
     * Readiness of a whole day's services in one grouped query.
     *
     * <p>The board needs "how many of this leg's services are confirmed", not the
     * rows themselves. Counting in the database keeps the payload at one row per
     * (leg, status) pair instead of one per request.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount(
                       s.legId, s.status, count(s))
            from ServiceRequest s
            where s.tenantId = :tenantId
              and s.legId in :legIds
            group by s.legId, s.status
            """)
    List<LegRequestCount> countByLegAndStatus(@Param("tenantId") UUID tenantId,
                                              @Param("legIds") Collection<UUID> legIds);

    @Query("""
            select s from ServiceRequest s
            where s.tenantId = :tenantId
              and s.legId = :legId
            order by s.stationIcao, s.serviceType
            """)
    List<ServiceRequest> findByLeg(@Param("tenantId") UUID tenantId, @Param("legId") UUID legId);

    /**
     * Every request of a day, in one statement.
     *
     * <p>The NetPlus Services board needs, per leg, which service types have
     * been requested — to name the ones that have not. One query for the whole
     * day rather than one per leg, exactly as the dispatch board does.
     */
    @Query("""
            select s from ServiceRequest s
            where s.tenantId = :tenantId
              and s.legId in :legIds
            order by s.legId, s.stationIcao, s.serviceType
            """)
    List<ServiceRequest> findByLegIds(@Param("tenantId") UUID tenantId,
                                      @Param("legIds") Collection<UUID> legIds);

    Optional<ServiceRequest> findByTenantIdAndLegIdAndStationIcaoAndServiceType(
            UUID tenantId, UUID legId, String stationIcao, GroundServiceType serviceType);

    Optional<ServiceRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
