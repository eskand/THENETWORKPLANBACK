package com.thenetworkplan.networkplan.roster.repository;

import com.thenetworkplan.networkplan.roster.domain.RosterEntry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, UUID> {

    /**
     * Every cell of a version, person loaded, in one statement.
     *
     * <p>A fortnight for fifty people is seven hundred cells: one query and one
     * pass in memory to lay them on the grid. Querying per person, or worse per
     * cell, is what makes a roster screen unusable at scale.
     */
    @Query("""
            select e from RosterEntry e
            join fetch e.person p
            where e.tenantId = :tenantId
              and e.rosterVersion.id = :versionId
            order by p.mainRole, p.lastName, e.dutyDate
            """)
    List<RosterEntry> findByVersion(@Param("tenantId") UUID tenantId, @Param("versionId") UUID versionId);

    /**
     * Every cell falling inside a window, whatever version wrote it.
     *
     * <p>The month grid needs the versions themselves — a cell has to say
     * whether it is published or drafted — so the version is fetched with the
     * row rather than loaded one proxy at a time.
     */
    @Query("""
            select e from RosterEntry e
            join fetch e.person p
            join fetch e.rosterVersion v
            where e.tenantId = :tenantId
              and e.dutyDate between :from and :to
              and v.status <> com.thenetworkplan.networkplan.roster.domain.RosterStatus.ARCHIVED
            order by p.mainRole, p.lastName, e.dutyDate
            """)
    List<RosterEntry> findByWindow(@Param("tenantId") UUID tenantId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
            select e from RosterEntry e
            join fetch e.person
            where e.tenantId = :tenantId
              and e.rosterVersion.id = :versionId
              and e.person.id = :personId
              and e.dutyDate = :day
            """)
    List<RosterEntry> findCell(@Param("tenantId") UUID tenantId,
                               @Param("versionId") UUID versionId,
                               @Param("personId") UUID personId,
                               @Param("day") LocalDate day);

    Optional<RosterEntry> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByRosterVersionId(UUID versionId);
}
