package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskMatrixRepository extends JpaRepository<RiskMatrixCell, UUID> {

    List<RiskMatrixCell> findByTenantIdOrderBySeverityAscProbabilityAsc(UUID tenantId);

    Optional<RiskMatrixCell> findByTenantIdAndSeverityAndProbability(
            UUID tenantId, String severity, int probability);
}
