package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.HazardControl;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HazardControlRepository extends JpaRepository<HazardControl, UUID> {

    /** Every barrier of the register in one read: the list shows a count per row. */
    List<HazardControl> findByTenantIdOrderBySortOrder(UUID tenantId);

    List<HazardControl> findByHazardIdOrderBySortOrder(UUID hazardId);
}
