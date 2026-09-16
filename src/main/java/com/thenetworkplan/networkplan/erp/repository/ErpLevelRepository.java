package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpLevelRepository extends JpaRepository<ErpLevel, UUID> {

    List<ErpLevel> findByTenantIdOrderByLevel(UUID tenantId);
}
