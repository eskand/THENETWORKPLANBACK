package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErpRoleRepository extends JpaRepository<ErpRole, UUID> {

    /** The call tree, in the order it is worked through. */
    @Query("""
            select r from ErpRole r
            where r.tenantId = :tenantId
              and r.plan.id = :planId
            order by r.callOrder
            """)
    List<ErpRole> findByPlan(@Param("tenantId") UUID tenantId, @Param("planId") UUID planId);
}
