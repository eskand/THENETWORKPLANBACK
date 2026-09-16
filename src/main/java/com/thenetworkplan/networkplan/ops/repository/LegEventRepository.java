package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.LegEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegEventRepository extends JpaRepository<LegEvent, UUID> {

    List<LegEvent> findByTenantIdAndLegIdOrderByCreatedAtDesc(UUID tenantId, UUID legId);
}
