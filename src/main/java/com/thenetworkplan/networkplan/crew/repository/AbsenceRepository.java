package com.thenetworkplan.networkplan.crew.repository;

import com.thenetworkplan.networkplan.crew.domain.Absence;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

    @Query("""
            select a from Absence a
            where a.tenantId = :tenantId
              and a.person.id = :personId
            order by a.startsOn desc
            """)
    List<Absence> findByPerson(@Param("tenantId") UUID tenantId, @Param("personId") UUID personId);

    /**
     * Absences overlapping a window, for a set of people.
     *
     * <p>Overlap, not containment: an absence that started last week and ends
     * next week makes the person unavailable today, and the naive
     * {@code starts_on between} test that the prototype used misses it.
     */
    @Query("""
            select a from Absence a
            join fetch a.person
            where a.tenantId = :tenantId
              and a.person.id in :personIds
              and a.startsOn <= :to
              and a.endsOn >= :from
            order by a.startsOn
            """)
    List<Absence> findOverlapping(@Param("tenantId") UUID tenantId,
                                  @Param("personIds") Collection<UUID> personIds,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query("""
            select a from Absence a
            join fetch a.person
            where a.tenantId = :tenantId
              and a.startsOn <= :to
              and a.endsOn >= :from
            order by a.startsOn
            """)
    List<Absence> findAllOverlapping(@Param("tenantId") UUID tenantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
}
