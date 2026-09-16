package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.OccurrenceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OccurrenceRepository extends JpaRepository<Occurrence, UUID> {

    /** How many occurrences are on file. The crisis console asks only this. */
    long countByTenantId(UUID tenantId);

    @Query("""
            select o from Occurrence o
            left join fetch o.aircraft
            left join fetch o.reportedBy
            where o.tenantId = :tenantId
              and (:status is null or o.status = :status)
              and o.occurredAt >= :since
            order by o.occurredAt desc
            """)
    List<Occurrence> findAll(@Param("tenantId") UUID tenantId,
                             @Param("status") OccurrenceStatus status,
                             @Param("since") OffsetDateTime since);

    @Query("""
            select o from Occurrence o
            left join fetch o.aircraft
            left join fetch o.reportedBy
            where o.tenantId = :tenantId
              and o.id = :id
            """)
    Optional<Occurrence> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    Optional<Occurrence> findByTenantIdAndReference(UUID tenantId, String reference);
}
