package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelayRecordRepository extends JpaRepository<DelayRecord, UUID> {

    List<DelayRecord> findByTenantIdAndLegIdIn(UUID tenantId, Collection<UUID> legIds);

    /**
     * Delay records of a window, joined to their leg for the date filter.
     *
     * <p>The reporting module asks DOM1 for this rather than reading the
     * table: one query, and the domain keeps its own SQL.
     */
    @org.springframework.data.jpa.repository.Query("""
            select d from DelayRecord d
            where d.tenantId = :tenantId
              and d.legId in (select l.id from Leg l
                              where l.tenantId = :tenantId
                                and l.std >= :from
                                and l.std < :to)
            """)
    List<DelayRecord> findInWindow(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                   @org.springframework.data.repository.query.Param("from") java.time.OffsetDateTime from,
                                   @org.springframework.data.repository.query.Param("to") java.time.OffsetDateTime to);
}
