package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpChecklistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpChecklistItemRepository extends JpaRepository<ErpChecklistItem, UUID> {

    List<ErpChecklistItem> findByTenantIdOrderByPhaseAscDeptCodeAscSortOrderAsc(UUID tenantId);
}
