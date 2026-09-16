package com.thenetworkplan.networkplan.sim.repository;

import com.thenetworkplan.networkplan.sim.domain.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    List<Scenario> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Scenario> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantId(UUID tenantId);
}
