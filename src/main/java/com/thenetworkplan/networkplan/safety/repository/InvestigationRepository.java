package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Investigation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {

    List<Investigation> findByTenantIdOrderByOpenedOnDesc(UUID tenantId);
}
