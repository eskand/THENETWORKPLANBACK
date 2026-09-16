package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.SafetyNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyNotificationRepository extends JpaRepository<SafetyNotification, UUID> {

    List<SafetyNotification> findByTenantIdOrderByAtDesc(UUID tenantId);

    Optional<SafetyNotification> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantIdAndReadAtIsNull(UUID tenantId);
}
