package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Hazard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HazardRepository extends JpaRepository<Hazard, UUID> {

    List<Hazard> findByTenantIdOrderByReference(UUID tenantId);

    Optional<Hazard> findByTenantIdAndId(UUID tenantId, UUID id);
}
