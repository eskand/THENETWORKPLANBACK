package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.SpiDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpiDefinitionRepository extends JpaRepository<SpiDefinition, UUID> {

    List<SpiDefinition> findByTenantIdOrderBySortOrderAsc(UUID tenantId);
}
