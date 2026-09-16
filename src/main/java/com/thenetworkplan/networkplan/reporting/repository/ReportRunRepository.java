package com.thenetworkplan.networkplan.reporting.repository;

import com.thenetworkplan.networkplan.reporting.domain.ReportRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRunRepository extends JpaRepository<ReportRun, UUID> {

    /** The last runs of every definition, in one statement. */
    @Query("""
            select r from ReportRun r
            join fetch r.definition
            where r.tenantId = :tenantId
            order by r.ranAt desc
            limit 100
            """)
    List<ReportRun> findRecent(@Param("tenantId") UUID tenantId);
}
