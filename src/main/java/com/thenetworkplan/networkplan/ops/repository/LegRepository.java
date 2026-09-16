package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.dto.LegStatusCount;
import com.thenetworkplan.networkplan.ops.dto.StationCount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegRepository extends JpaRepository<Leg, UUID> {

    /**
     * The whole programme of a window, with everything the board renders.
     *
     * <p>Three {@code join fetch} on single-valued associations: aircraft, its
     * type and the trip. One SQL statement, no cartesian product, no lazy load
     * while mapping. The equivalent lazy version costs 1 + 3n queries — for a
     * 32-leg day that is 97 round trips instead of 1.
     *
     * <p>The fleet, base and tab filters are applied on the returned list rather
     * than in SQL: a day's programme is bounded (tens of rows), and keeping the
     * query static avoids the nullable-parameter casts that make dynamic JPQL
     * fragile. If the window ever spans months, this becomes a Specification with
     * an entity graph.
     */
    @Query("""
            select l from Leg l
            join fetch l.aircraft a
            join fetch a.aircraftType t
            left join fetch l.trip
            where l.tenantId = :tenantId
              and l.std >= :from
              and l.std < :to
            order by l.std asc, l.flightNo asc
            """)
    List<Leg> findProgramme(@Param("tenantId") UUID tenantId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to);

    @Query("""
            select l from Leg l
            join fetch l.aircraft a
            join fetch a.aircraftType t
            left join fetch l.trip
            where l.tenantId = :tenantId
              and l.id = :id
            """)
    Optional<Leg> findOneWithDetails(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select count(l) from Leg l
            where l.tenantId = :tenantId
              and l.std >= :from
              and l.std < :to
            """)
    long countProgramme(@Param("tenantId") UUID tenantId,
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

    @Query("""
            select count(distinct l.aircraft.id) from Leg l
            where l.tenantId = :tenantId
              and l.std >= :from
              and l.std < :to
            """)
    long countDistinctAircraft(@Param("tenantId") UUID tenantId,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to);

    @Query("""
            select new com.thenetworkplan.networkplan.ops.dto.LegStatusCount(l.status, count(l))
            from Leg l
            where l.tenantId = :tenantId
              and l.std >= :from
              and l.std < :to
            group by l.status
            """)
    List<LegStatusCount> countByStatus(@Param("tenantId") UUID tenantId,
                                       @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to);

    /** Departures per station since an instant, for the aerodrome directory. */
    @Query("""
            select new com.thenetworkplan.networkplan.ops.dto.StationCount(l.depIcao, count(l))
            from Leg l
            where l.tenantId = :tenantId
              and l.std >= :since
            group by l.depIcao
            """)
    List<StationCount> countDeparturesByStation(@Param("tenantId") UUID tenantId,
                                                @Param("since") OffsetDateTime since);

    /** Arrivals per station since an instant. */
    @Query("""
            select new com.thenetworkplan.networkplan.ops.dto.StationCount(l.arrIcao, count(l))
            from Leg l
            where l.tenantId = :tenantId
              and l.std >= :since
            group by l.arrIcao
            """)
    List<StationCount> countArrivalsByStation(@Param("tenantId") UUID tenantId,
                                              @Param("since") OffsetDateTime since);

    boolean existsByBusinessKey(String businessKey);

    /**
     * Next rotation of the same tail, used by the delay cascade: a delay pushes
     * the following leg only when the turnaround falls under the tenant minimum.
     */
    @Query("""
            select l from Leg l
            join fetch l.aircraft a
            join fetch a.aircraftType t
            where l.tenantId = :tenantId
              and a.id = :aircraftId
              and l.std > :after
              and l.status in (com.thenetworkplan.networkplan.ops.domain.LegStatus.PLANNED,
                               com.thenetworkplan.networkplan.ops.domain.LegStatus.PREPARED,
                               com.thenetworkplan.networkplan.ops.domain.LegStatus.RELEASED)
            order by l.std asc
            """)
    List<Leg> findNextRotations(@Param("tenantId") UUID tenantId,
                                @Param("aircraftId") UUID aircraftId,
                                @Param("after") OffsetDateTime after);
}
