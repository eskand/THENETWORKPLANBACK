package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByTenantIdOrderByStartsOnDesc(UUID tenantId);

    Optional<Campaign> findByTenantIdAndId(UUID tenantId, UUID id);
}
