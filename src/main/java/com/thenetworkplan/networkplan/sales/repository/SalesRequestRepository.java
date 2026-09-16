package com.thenetworkplan.networkplan.sales.repository;

import com.thenetworkplan.networkplan.sales.domain.SalesRequest;
import com.thenetworkplan.networkplan.sales.domain.SalesRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesRequestRepository extends JpaRepository<SalesRequest, UUID> {

    /** Client and type fetch-joined: the list shows both on every row. */
    @Query("""
            select r from SalesRequest r
            join fetch r.client
            left join fetch r.aircraftType
            where r.tenantId = :tenantId
              and (:status is null or r.status = :status)
            order by r.receivedAt desc
            """)
    List<SalesRequest> findAll(@Param("tenantId") UUID tenantId,
                               @Param("status") SalesRequestStatus status);

    @Query("""
            select r from SalesRequest r
            join fetch r.client
            left join fetch r.aircraftType
            where r.tenantId = :tenantId
              and r.id = :id
            """)
    Optional<SalesRequest> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    Optional<SalesRequest> findByTenantIdAndReference(UUID tenantId, String reference);
}
