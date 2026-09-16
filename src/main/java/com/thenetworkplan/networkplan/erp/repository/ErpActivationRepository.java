package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpActivation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErpActivationRepository extends JpaRepository<ErpActivation, UUID> {

    @Query("""
            select a from ErpActivation a
            join fetch a.plan
            where a.tenantId = :tenantId
            order by a.activatedAt desc
            """)
    List<ErpActivation> findAllForTenant(@Param("tenantId") UUID tenantId);

    Optional<ErpActivation> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ErpActivation> findByTenantIdAndReference(UUID tenantId, String reference);
}
