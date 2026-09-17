package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.InvestigationStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestigationStepRepository extends JpaRepository<InvestigationStep, UUID> {

    /**
     * Every chain of the tenant in one query, ordered.
     *
     * <p>The investigations page shows them all at once; asking per
     * investigation would put a query inside the loop that renders the list.
     */
    @Query("""
            select s from InvestigationStep s
            where s.tenantId = :tenantId
            order by s.investigation.id, s.position
            """)
    List<InvestigationStep> findAllForTenant(@Param("tenantId") UUID tenantId);
}
