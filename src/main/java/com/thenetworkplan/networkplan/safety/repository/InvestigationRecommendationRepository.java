package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.InvestigationRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestigationRecommendationRepository
        extends JpaRepository<InvestigationRecommendation, UUID> {

    @Query("""
            select r from InvestigationRecommendation r
            where r.tenantId = :tenantId
            order by r.investigation.id, r.position
            """)
    List<InvestigationRecommendation> findAllForTenant(@Param("tenantId") UUID tenantId);
}
