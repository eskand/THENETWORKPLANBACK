package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.LegPassenger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegPassengerRepository extends JpaRepository<LegPassenger, UUID> {

    List<LegPassenger> findByTenantIdAndLegIdOrderBySeqAsc(UUID tenantId, UUID legId);

    Optional<LegPassenger> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantIdAndLegId(UUID tenantId, UUID legId);
}
