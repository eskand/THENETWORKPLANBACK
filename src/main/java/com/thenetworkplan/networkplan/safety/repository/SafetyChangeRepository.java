package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.SafetyChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyChangeRepository extends JpaRepository<SafetyChange, UUID> {

    List<SafetyChange> findByTenantIdOrderByRaisedOnDesc(UUID tenantId);
}
