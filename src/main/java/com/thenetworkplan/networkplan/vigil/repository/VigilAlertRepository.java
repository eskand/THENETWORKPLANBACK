package com.thenetworkplan.networkplan.vigil.repository;

import com.thenetworkplan.networkplan.vigil.domain.VigilAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VigilAlertRepository extends JpaRepository<VigilAlert, UUID> {

    List<VigilAlert> findByTenantId(UUID tenantId);

    Optional<VigilAlert> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Les alertes actives, la plus grave d'abord — l'ordre du panneau. */
    @Query("""
            select a from VigilAlert a
            where a.tenantId = :tenantId
              and a.status in (com.thenetworkplan.networkplan.vigil.domain.VigilAlertStatus.OPEN,
                               com.thenetworkplan.networkplan.vigil.domain.VigilAlertStatus.ACKNOWLEDGED,
                               com.thenetworkplan.networkplan.vigil.domain.VigilAlertStatus.IN_PROGRESS)
            order by case a.severity
                        when com.thenetworkplan.networkplan.vigil.domain.VigilSeverity.CRITICAL then 0
                        when com.thenetworkplan.networkplan.vigil.domain.VigilSeverity.HIGH then 1
                        when com.thenetworkplan.networkplan.vigil.domain.VigilSeverity.WARNING then 2
                        else 3
                     end,
                     a.createdAt desc
            """)
    List<VigilAlert> findActive(@Param("tenantId") UUID tenantId);
}
