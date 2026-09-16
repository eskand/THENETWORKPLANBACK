package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpPlanRepository extends JpaRepository<ErpPlan, UUID> {

    List<ErpPlan> findByTenantIdOrderByApprovedOnDesc(UUID tenantId);

    Optional<ErpPlan> findByTenantIdAndId(UUID tenantId, UUID id);
}
