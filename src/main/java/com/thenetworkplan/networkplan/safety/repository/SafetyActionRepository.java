package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.SafetyAction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SafetyActionRepository extends JpaRepository<SafetyAction, UUID> {

    /** Every action of a set of occurrences, in one statement. */
    @Query("""
            select a from SafetyAction a
            where a.tenantId = :tenantId
              and a.occurrence.id in :occurrenceIds
            order by a.occurrence.id, a.dueOn nulls last
            """)
    List<SafetyAction> findByOccurrenceIds(@Param("tenantId") UUID tenantId,
                                           @Param("occurrenceIds") Collection<UUID> occurrenceIds);

    @Query("""
            select a from SafetyAction a
            left join fetch a.occurrence
            where a.tenantId = :tenantId
              and a.status in (com.thenetworkplan.networkplan.safety.domain.ActionStatus.OPEN,
                               com.thenetworkplan.networkplan.safety.domain.ActionStatus.IN_PROGRESS)
            order by a.dueOn nulls last
            """)
    List<SafetyAction> findOutstanding(@Param("tenantId") UUID tenantId);

    Optional<SafetyAction> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantId(UUID tenantId);
}
