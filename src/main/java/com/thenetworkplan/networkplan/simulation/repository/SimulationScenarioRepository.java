package com.thenetworkplan.networkplan.simulation.repository;

import com.thenetworkplan.networkplan.simulation.domain.SimulationScenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationScenarioRepository extends JpaRepository<SimulationScenario, UUID> {

    List<SimulationScenario> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<SimulationScenario> findByTenantIdAndId(UUID tenantId, UUID id);
}
