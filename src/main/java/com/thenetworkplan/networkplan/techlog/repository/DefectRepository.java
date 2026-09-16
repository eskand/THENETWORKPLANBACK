package com.thenetworkplan.networkplan.techlog.repository;

import com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount;
import com.thenetworkplan.networkplan.techlog.domain.Defect;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DefectRepository extends JpaRepository<Defect, UUID> {

    @Query("""
            select d from Defect d
            join fetch d.aircraft a
            join fetch a.aircraftType
            left join fetch d.reportedBy
            where d.tenantId = :tenantId
              and (:aircraftId is null or a.id = :aircraftId)
              and (:openOnly = false
                   or d.status <> com.thenetworkplan.networkplan.techlog.domain.DefectStatus.CLOSED)
            order by d.reportedAt desc
            """)
    List<Defect> findDefects(@Param("tenantId") UUID tenantId,
                             @Param("aircraftId") UUID aircraftId,
                             @Param("openOnly") boolean openOnly);

    @Query("""
            select d from Defect d
            left join fetch d.reportedBy
            where d.tenantId = :tenantId
              and d.techLogEntry.id in :entryIds
            order by d.reportedAt
            """)
    List<Defect> findByEntryIds(@Param("tenantId") UUID tenantId,
                                @Param("entryIds") Collection<UUID> entryIds);

    /** Outstanding defects per registration, counted in the database. */
    @Query("""
            select new com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount(
                       d.aircraft.id, count(d))
            from Defect d
            where d.tenantId = :tenantId
              and d.status <> com.thenetworkplan.networkplan.techlog.domain.DefectStatus.CLOSED
            group by d.aircraft.id
            """)
    List<AircraftCount> countOutstandingByAircraft(@Param("tenantId") UUID tenantId);

    Optional<Defect> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * The defects that were deferred under an MEL item.
     *
     * <p>The Hold Item List reads this to say where each line came from and
     * which ATA chapter it sits in: the deferral itself records neither, and
     * inventing an ATA on the screen would be worse than showing none.
     */
    List<Defect> findByTenantIdAndMelItemIdNotNull(UUID tenantId);
}
