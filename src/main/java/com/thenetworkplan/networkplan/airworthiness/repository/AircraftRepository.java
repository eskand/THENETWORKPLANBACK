package com.thenetworkplan.networkplan.airworthiness.repository;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {

    /**
     * The whole fleet with its type in one statement.
     *
     * <p>{@code join fetch} on a single-valued association: one SQL join, no
     * cartesian product, and no lazy load when the mapper reads
     * {@code getAircraftType().getIcaoType()}. Without it, rendering 23 rows costs
     * 24 queries.
     */
    @Query("""
            select a from Aircraft a
            join fetch a.aircraftType
            where a.tenantId = :tenantId
            order by a.registration
            """)
    List<Aircraft> findFleet(@Param("tenantId") UUID tenantId);

    @Query("""
            select a from Aircraft a
            join fetch a.aircraftType
            where a.tenantId = :tenantId
              and a.status in :statuses
            order by a.registration
            """)
    List<Aircraft> findFleetByStatus(@Param("tenantId") UUID tenantId,
                                     @Param("statuses") Collection<AircraftStatus> statuses);

    @Query("""
            select a from Aircraft a
            join fetch a.aircraftType
            where a.id = :id
              and a.tenantId = :tenantId
            """)
    Optional<Aircraft> findOneWithType(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select a from Aircraft a
            join fetch a.aircraftType
            where a.tenantId = :tenantId
              and a.registration = :registration
            """)
    Optional<Aircraft> findByRegistration(@Param("tenantId") UUID tenantId,
                                          @Param("registration") String registration);
}
