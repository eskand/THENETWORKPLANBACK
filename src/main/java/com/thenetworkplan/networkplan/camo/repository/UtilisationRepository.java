package com.thenetworkplan.networkplan.camo.repository;

import com.thenetworkplan.networkplan.camo.domain.Utilisation;
import com.thenetworkplan.networkplan.camo.dto.AircraftUtilisation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtilisationRepository extends JpaRepository<Utilisation, UUID> {

    /**
     * Block minutes and cycles per aircraft since a date, counted in the
     * database — the same {@code group by} device as the crew counters.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.camo.dto.AircraftUtilisation(
                       u.aircraft.id, sum(u.blockMinutes), sum(u.cycles), count(u))
            from Utilisation u
            where u.tenantId = :tenantId
              and u.flownOn >= :since
            group by u.aircraft.id
            """)
    List<AircraftUtilisation> summariseSince(@Param("tenantId") UUID tenantId,
                                             @Param("since") LocalDate since);

    Optional<Utilisation> findByTenantIdAndLegId(UUID tenantId, UUID legId);

    @Query("""
            select u from Utilisation u
            where u.tenantId = :tenantId
              and u.aircraft.id = :aircraftId
            order by u.flownOn desc
            limit 60
            """)
    List<Utilisation> findRecentForAircraft(@Param("tenantId") UUID tenantId,
                                            @Param("aircraftId") UUID aircraftId);
}
