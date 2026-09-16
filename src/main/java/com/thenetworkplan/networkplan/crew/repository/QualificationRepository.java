package com.thenetworkplan.networkplan.crew.repository;

import com.thenetworkplan.networkplan.crew.domain.Qualification;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QualificationRepository extends JpaRepository<Qualification, UUID> {

    /**
     * Qualifications of a set of people, with the aircraft type already loaded.
     *
     * <p>Same reason as {@code AircraftRepository.findFleet}: the mapper reads
     * {@code getAircraftType().getIcaoType()} on every row, so the type is
     * fetch-joined once instead of being lazily loaded per row. The join is a
     * {@code left join fetch} because CRM, SEP and dangerous goods carry no type.
     */
    @Query("""
            select q from Qualification q
            left join fetch q.aircraftType
            where q.tenantId = :tenantId
              and q.person.id in :personIds
            order by q.kind, q.validTo
            """)
    List<Qualification> findByPersonIds(@Param("tenantId") UUID tenantId,
                                        @Param("personIds") Collection<UUID> personIds);

    @Query("""
            select q from Qualification q
            left join fetch q.aircraftType
            where q.tenantId = :tenantId
              and q.person.id = :personId
            order by q.kind, q.validTo
            """)
    List<Qualification> findByPerson(@Param("tenantId") UUID tenantId, @Param("personId") UUID personId);

    /**
     * The expiry wall: everything that lapses before a date, worst first.
     * Rows with no {@code valid_to} are returned too — an unknown expiry is a
     * finding, not a pass.
     */
    @Query("""
            select q from Qualification q
            left join fetch q.aircraftType
            join fetch q.person p
            where q.tenantId = :tenantId
              and (q.validTo is null or q.validTo <= :horizon)
              and p.active = true
            order by q.validTo nulls first
            """)
    List<Qualification> findExpiringBefore(@Param("tenantId") UUID tenantId,
                                           @Param("horizon") LocalDate horizon);
}
