package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.Release;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReleaseRepository extends JpaRepository<Release, UUID> {

    Optional<Release> findFirstByTenantIdAndLegIdOrderByVersionDesc(UUID tenantId, UUID legId);

    @Query("""
            select r from Release r
            where r.tenantId = :tenantId
              and r.legId in :legIds
            order by r.legId, r.version desc
            """)
    List<Release> findByLegIds(@Param("tenantId") UUID tenantId, @Param("legIds") List<UUID> legIds);
}
