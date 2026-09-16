package com.thenetworkplan.networkplan.crew.repository;

import com.thenetworkplan.networkplan.crew.domain.CrewAssignment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, UUID> {

    /**
     * Crew of a whole day's programme in one statement.
     *
     * <p>{@code join fetch a.person} is what turns "32 legs on the board" from
     * 65 queries into one: without it every mapped row lazy-loads its person.
     */
    @Query("""
            select a from CrewAssignment a
            join fetch a.person
            where a.tenantId = :tenantId
              and a.legId in :legIds
            order by a.legId, a.seat
            """)
    List<CrewAssignment> findByLegIds(@Param("tenantId") UUID tenantId,
                                      @Param("legIds") Collection<UUID> legIds);

    @Query("""
            select a from CrewAssignment a
            join fetch a.person
            where a.tenantId = :tenantId
              and a.legId = :legId
            order by a.seat
            """)
    List<CrewAssignment> findByLeg(@Param("tenantId") UUID tenantId, @Param("legId") UUID legId);
}
