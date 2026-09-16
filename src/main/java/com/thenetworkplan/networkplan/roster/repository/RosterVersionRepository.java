package com.thenetworkplan.networkplan.roster.repository;

import com.thenetworkplan.networkplan.roster.domain.RosterStatus;
import com.thenetworkplan.networkplan.roster.domain.RosterVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RosterVersionRepository extends JpaRepository<RosterVersion, UUID> {

    List<RosterVersion> findByTenantIdOrderByPeriodStartDesc(UUID tenantId);

    Optional<RosterVersion> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * The published version covering a day.
     *
     * <p>The unique constraint allows several labels over one period, so the
     * most recently published wins — which is what a crew member reads.
     */
    @Query("""
            select v from RosterVersion v
            where v.tenantId = :tenantId
              and v.status = :status
              and v.periodStart <= :day
              and v.periodEnd >= :day
            order by v.publishedAt desc
            limit 1
            """)
    Optional<RosterVersion> findCovering(@Param("tenantId") UUID tenantId,
                                         @Param("day") LocalDate day,
                                         @Param("status") RosterStatus status);
}
