package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.CampaignAcknowledgement;
import com.thenetworkplan.networkplan.safety.dto.CampaignCount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignAcknowledgementRepository extends JpaRepository<CampaignAcknowledgement, UUID> {

    /** Acknowledgements per campaign, counted in the database. */
    @Query("""
            select new com.thenetworkplan.networkplan.safety.dto.CampaignCount(
                       a.campaign.id, count(a))
            from CampaignAcknowledgement a
            where a.tenantId = :tenantId
            group by a.campaign.id
            """)
    List<CampaignCount> countByCampaign(@Param("tenantId") UUID tenantId);

    boolean existsByCampaignIdAndPersonId(UUID campaignId, UUID personId);
}
