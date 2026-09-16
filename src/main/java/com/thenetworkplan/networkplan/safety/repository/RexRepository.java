package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Rex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RexRepository extends JpaRepository<Rex, UUID> {

    List<Rex> findByTenantIdOrderByPublishedOnDesc(UUID tenantId);

    Optional<Rex> findByTenantIdAndId(UUID tenantId, UUID id);
}
