package com.thenetworkplan.networkplan.camo.repository;

import com.thenetworkplan.networkplan.camo.domain.AircraftTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AircraftTaskRepository extends JpaRepository<AircraftTask, UUID> {

    /**
     * Every open task of the fleet, aircraft and type loaded.
     *
     * <p>The due rule needs the aircraft counters on every row, so the
     * association is fetch-joined rather than lazily loaded per task: 184 tasks
     * would otherwise cost 185 statements.
     */
    @Query("""
            select t from AircraftTask t
            join fetch t.aircraft a
            join fetch a.aircraftType
            where t.tenantId = :tenantId
              and t.closedAt is null
            order by t.dueOn nulls last, t.code
            """)
    List<AircraftTask> findOpen(@Param("tenantId") UUID tenantId);

    @Query("""
            select t from AircraftTask t
            join fetch t.aircraft a
            join fetch a.aircraftType
            where t.tenantId = :tenantId
              and t.aircraft.id = :aircraftId
            order by t.closedAt nulls first, t.dueOn nulls last
            """)
    List<AircraftTask> findByAircraft(@Param("tenantId") UUID tenantId,
                                      @Param("aircraftId") UUID aircraftId);

    @Query("""
            select t from AircraftTask t
            join fetch t.aircraft a
            join fetch a.aircraftType
            where t.tenantId = :tenantId
              and t.id = :id
            """)
    Optional<AircraftTask> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
