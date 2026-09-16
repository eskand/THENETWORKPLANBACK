package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.AuditFinding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, UUID> {

    /**
     * Every finding with its audit loaded.
     *
     * <p>Fetch-joined because the dashboard groups findings by audit and would
     * otherwise issue one statement per finding to read the audit's name.
     */
    @Query("""
            select f from AuditFinding f
            join fetch f.audit a
            where f.tenantId = :tenantId
            order by f.dueOn nulls last, f.reference
            """)
    List<AuditFinding> findAllWithAudit(@Param("tenantId") UUID tenantId);
}
