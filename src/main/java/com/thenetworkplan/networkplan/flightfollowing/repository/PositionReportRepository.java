package com.thenetworkplan.networkplan.flightfollowing.repository;

import com.thenetworkplan.networkplan.flightfollowing.domain.PositionReport;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionReportRepository extends JpaRepository<PositionReport, UUID> {

    /**
     * The latest position of each leg in one statement.
     *
     * <p>A lateral "top 1 per group" would need a window function; with a
     * bounded window (the legs of the day) it is cheaper and clearer to read
     * the ordered rows once and keep the first per leg in the service.
     */
    @Query("""
            select p from PositionReport p
            join fetch p.aircraft a
            where p.tenantId = :tenantId
              and p.legId in :legIds
            order by p.legId, p.reportedAt desc
            """)
    List<PositionReport> findForLegs(@Param("tenantId") UUID tenantId,
                                     @Param("legIds") Collection<UUID> legIds);

    /** The track of one leg, oldest first: what the map draws. */
    @Query("""
            select p from PositionReport p
            where p.tenantId = :tenantId
              and p.legId = :legId
            order by p.reportedAt
            """)
    List<PositionReport> findTrack(@Param("tenantId") UUID tenantId, @Param("legId") UUID legId);

    @Query("""
            select p from PositionReport p
            join fetch p.aircraft
            where p.tenantId = :tenantId
              and p.reportedAt >= :since
            order by p.reportedAt desc
            """)
    List<PositionReport> findSince(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);

    /**
     * La position la plus recente connue pour un appareil.
     *
     * <p>Sert a ne pas reecrire un message deja stocke : le flux ADS-B renvoie
     * le meme etat tant que l'appareil n'a pas bouge, et un tableau rafraichi
     * toutes les quinze secondes remplirait la table de doublons.
     */
    @Query("""
            select p from PositionReport p
            where p.tenantId = :tenantId and p.aircraft.id = :aircraftId
            order by p.reportedAt desc
            limit 1
            """)
    Optional<PositionReport> findLatestForAircraft(@Param("tenantId") UUID tenantId,
                                                   @Param("aircraftId") UUID aircraftId);
}
