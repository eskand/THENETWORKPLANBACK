package com.thenetworkplan.networkplan.airworthiness.repository;

import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MelItemRepository extends JpaRepository<MelItem, UUID> {

    /**
     * Every open MEL of the fleet, with its aircraft, in one query. The dispatch
     * board groups them in memory: the list is a few dozen rows at most, and one
     * query beats one per tail.
     */
    @Query("""
            select m from MelItem m
            join fetch m.aircraft a
            join fetch a.aircraftType
            where m.tenantId = :tenantId
              and m.closedAt is null
            order by m.dueAt asc nulls first
            """)
    List<MelItem> findOpenForFleet(@Param("tenantId") UUID tenantId);

    @Query("""
            select m from MelItem m
            join fetch m.aircraft a
            where m.tenantId = :tenantId
              and a.id = :aircraftId
              and m.closedAt is null
            order by m.dueAt asc nulls first
            """)
    List<MelItem> findOpenForAircraft(@Param("tenantId") UUID tenantId, @Param("aircraftId") UUID aircraftId);

    /** Open and cleared items of one registration: the MEL history of a tail. */
    @Query("""
            select m from MelItem m
            join fetch m.aircraft a
            join fetch a.aircraftType
            where m.tenantId = :tenantId
              and a.id = :aircraftId
            order by m.closedAt asc nulls first, m.raisedAt desc
            """)
    List<MelItem> findAllForAircraft(@Param("tenantId") UUID tenantId, @Param("aircraftId") UUID aircraftId);
}
