package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.GroundStaff;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroundStaffRepository extends JpaRepository<GroundStaff, UUID> {

    List<GroundStaff> findByTenantIdOrderBySortOrder(UUID tenantId);

    /**
     * Everyone holding a given post.
     *
     * <p>A list rather than an Optional: two people can share a title, and a
     * crisis console that silently picks one of them is worse than one that
     * shows both.
     */
    List<GroundStaff> findByTenantIdAndRoleTitle(UUID tenantId, String roleTitle);
}
