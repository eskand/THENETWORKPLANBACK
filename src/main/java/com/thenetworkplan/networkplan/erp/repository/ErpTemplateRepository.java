package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpTemplateRepository extends JpaRepository<ErpTemplate, UUID> {

    List<ErpTemplate> findByTenantIdOrderBySortOrder(UUID tenantId);
}
