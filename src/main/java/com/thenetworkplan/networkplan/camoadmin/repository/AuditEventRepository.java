package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** The trail, newest first. Paged: it only ever grows. */
    List<AuditEvent> findByTenantIdOrderByAtDesc(UUID tenantId, Pageable pageable);

    List<AuditEvent> findByTenantIdAndEntityAndEntityIdOrderByAtDesc(
            UUID tenantId, String entity, String entityId);
}
