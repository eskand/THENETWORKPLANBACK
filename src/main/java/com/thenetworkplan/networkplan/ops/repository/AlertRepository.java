package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.Alert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query("""
            select a from Alert a
            where a.tenantId = :tenantId
              and a.ackedAt is null
            order by case a.severity
                        when com.thenetworkplan.networkplan.ops.domain.AlertSeverity.CRITICAL then 0
                        when com.thenetworkplan.networkplan.ops.domain.AlertSeverity.ATTENTION then 1
                        else 2
                     end,
                     a.createdAt desc
            """)
    List<Alert> findOpen(@Param("tenantId") UUID tenantId);

    Optional<Alert> findByTenantIdAndId(UUID tenantId, UUID id);
}
