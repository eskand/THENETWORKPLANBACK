package com.thenetworkplan.networkplan.techlog.repository;

import com.thenetworkplan.networkplan.techlog.domain.TechLogEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechLogEntryRepository extends JpaRepository<TechLogEntry, UUID> {

    @Query("""
            select e from TechLogEntry e
            join fetch e.aircraft a
            join fetch a.aircraftType
            left join fetch e.commander
            left join fetch e.engineer
            where e.tenantId = :tenantId
              and (:aircraftId is null or a.id = :aircraftId)
              and e.flownOn >= :from
              and e.flownOn <= :to
            order by e.flownOn desc, e.pageRef desc
            """)
    List<TechLogEntry> findPages(@Param("tenantId") UUID tenantId,
                                 @Param("aircraftId") UUID aircraftId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("""
            select e from TechLogEntry e
            join fetch e.aircraft a
            join fetch a.aircraftType
            left join fetch e.commander
            left join fetch e.engineer
            where e.tenantId = :tenantId
              and e.id = :id
            """)
    Optional<TechLogEntry> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    Optional<TechLogEntry> findByTenantIdAndLegId(UUID tenantId, UUID legId);

    boolean existsByTenantIdAndPageRef(UUID tenantId, String pageRef);
}
