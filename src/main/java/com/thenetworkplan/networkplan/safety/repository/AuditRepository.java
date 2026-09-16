package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.Audit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<Audit, UUID> {

    /** The programme in the order it runs, which is the order it is read in. */
    List<Audit> findByTenantIdOrderByPlannedOnAsc(UUID tenantId);
}
