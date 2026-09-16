package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveApplication;
import com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectiveApplicationRepository extends JpaRepository<DirectiveApplication, UUID> {

    @Query("""
            select a from DirectiveApplication a
            join fetch a.aircraft ac
            join fetch a.directive d
            where a.tenantId = :tenantId
              and d.id = :directiveId
            order by ac.registration
            """)
    List<DirectiveApplication> findByDirective(@Param("tenantId") UUID tenantId,
                                               @Param("directiveId") UUID directiveId);

    /**
     * Every application of the tenant, both sides loaded.
     *
     * <p>The directive list needs them all: one statement here instead of one
     * per directive, which is the same reason the dispatch board fetch-joins
     * its crew rather than looping.
     */
    @Query("""
            select a from DirectiveApplication a
            join fetch a.aircraft ac
            join fetch a.directive d
            where a.tenantId = :tenantId
            order by d.reference, ac.registration
            """)
    List<DirectiveApplication> findAllForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            select a from DirectiveApplication a
            join fetch a.directive
            where a.tenantId = :tenantId
              and a.aircraft.id = :aircraftId
            order by a.status, a.directive.reference
            """)
    List<DirectiveApplication> findByAircraft(@Param("tenantId") UUID tenantId,
                                              @Param("aircraftId") UUID aircraftId);

    /**
     * Outstanding directives per aircraft, counted in the database.
     *
     * <p>The CAMO fleet screen shows this figure on every row; counting it here
     * keeps that screen at a fixed number of statements.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount(
                       a.aircraft.id, count(a))
            from DirectiveApplication a
            where a.tenantId = :tenantId
              and a.status in (com.thenetworkplan.networkplan.camoadmin.domain.DirectiveStatus.OPEN,
                               com.thenetworkplan.networkplan.camoadmin.domain.DirectiveStatus.DEFERRED)
            group by a.aircraft.id
            """)
    List<AircraftCount> countOutstandingByAircraft(@Param("tenantId") UUID tenantId);

    Optional<DirectiveApplication> findByTenantIdAndId(UUID tenantId, UUID id);
}
