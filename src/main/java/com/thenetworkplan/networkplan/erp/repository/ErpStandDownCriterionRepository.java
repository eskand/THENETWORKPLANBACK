package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpStandDownCriterion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpStandDownCriterionRepository extends JpaRepository<ErpStandDownCriterion, UUID> {

    List<ErpStandDownCriterion> findByTenantIdOrderBySortOrder(UUID tenantId);
}
