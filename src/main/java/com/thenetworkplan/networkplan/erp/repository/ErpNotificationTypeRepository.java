package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpNotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpNotificationTypeRepository extends JpaRepository<ErpNotificationType, UUID> {

    List<ErpNotificationType> findByTenantIdOrderBySortOrder(UUID tenantId);
}
