package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpActivationNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpActivationNotificationRepository
        extends JpaRepository<ErpActivationNotification, UUID> {

    List<ErpActivationNotification> findByActivationId(UUID activationId);

    Optional<ErpActivationNotification> findByActivationIdAndTypeCode(UUID activationId, String typeCode);
}
