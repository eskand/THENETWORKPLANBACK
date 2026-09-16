package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.Directive;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectiveRepository extends JpaRepository<Directive, UUID> {

    @Query("""
            select d from Directive d
            left join fetch d.aircraftType
            where d.tenantId = :tenantId
            order by d.complianceByDate nulls last, d.reference
            """)
    List<Directive> findAllForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            select d from Directive d
            left join fetch d.aircraftType
            where d.tenantId = :tenantId
              and d.id = :id
            """)
    Optional<Directive> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    Optional<Directive> findByTenantIdAndReference(UUID tenantId, String reference);
}
