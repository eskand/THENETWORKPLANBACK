package com.thenetworkplan.networkplan.crew.repository;

import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.dto.PersonInstant;
import com.thenetworkplan.networkplan.crew.dto.PersonMinutes;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DutyPeriodRepository extends JpaRepository<DutyPeriod, UUID> {

    /**
     * Block minutes flown per person since an instant, in one statement.
     *
     * <p>One query answers the whole crew list, whatever its size. Persons with
     * no flying in the window are simply absent from the result and are read as
     * zero by the service — which is a fact, not a default.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.crew.dto.PersonMinutes(
                       d.person.id, sum(d.blockMinutes))
            from DutyPeriod d
            where d.tenantId = :tenantId
              and d.reportAt >= :since
              and d.blockMinutes is not null
            group by d.person.id
            """)
    List<PersonMinutes> sumBlockMinutesSince(@Param("tenantId") UUID tenantId,
                                             @Param("since") OffsetDateTime since);

    /**
     * The same sum, bounded at both ends: the hours a roster period holds.
     *
     * <p>The window is closed on the report time, not on the off-duty time. A
     * duty that reports on the last day of the month and lands on the first of
     * the next belongs to the month it started in — which is how a crew member
     * reads their own line.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.crew.dto.PersonMinutes(
                       d.person.id, sum(d.blockMinutes))
            from DutyPeriod d
            where d.tenantId = :tenantId
              and d.reportAt >= :from
              and d.reportAt < :to
              and d.blockMinutes is not null
            group by d.person.id
            """)
    List<PersonMinutes> sumBlockMinutesBetween(@Param("tenantId") UUID tenantId,
                                               @Param("from") OffsetDateTime from,
                                               @Param("to") OffsetDateTime to);

    /**
     * Duty minutes per person since an instant. Rest and days off are excluded
     * here rather than in Java: {@code DutyKind.countsAsDuty()} and this filter
     * must say the same thing, and the test in {@code DutyKindTest} pins them.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.crew.dto.PersonMinutes(
                       d.person.id,
                       sum(timestampdiff(minute, d.reportAt, d.offDutyAt)))
            from DutyPeriod d
            where d.tenantId = :tenantId
              and d.reportAt >= :since
              and d.kind not in (com.thenetworkplan.networkplan.crew.domain.DutyKind.REST,
                                 com.thenetworkplan.networkplan.crew.domain.DutyKind.OFF)
            group by d.person.id
            """)
    List<PersonMinutes> sumDutyMinutesSince(@Param("tenantId") UUID tenantId,
                                            @Param("since") OffsetDateTime since);

    @Query("""
            select d from DutyPeriod d
            join fetch d.person
            where d.tenantId = :tenantId
              and d.person.id = :personId
              and d.reportAt >= :from
              and d.reportAt < :to
            order by d.reportAt
            """)
    List<DutyPeriod> findByPersonInWindow(@Param("tenantId") UUID tenantId,
                                          @Param("personId") UUID personId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to);

    /**
     * Every duty of the tenant inside a window, person fetched.
     *
     * <p>Feeds both the roster grid and the scheduling board: one query, two
     * read models, rather than one query per screen.
     */
    @Query("""
            select d from DutyPeriod d
            join fetch d.person
            where d.tenantId = :tenantId
              and d.reportAt >= :from
              and d.reportAt < :to
            order by d.person.lastName, d.reportAt
            """)
    List<DutyPeriod> findInWindow(@Param("tenantId") UUID tenantId,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    /**
     * The last off-duty instant of every person before a moment, in one query.
     *
     * <p>The scheduling pool needs this for the whole crew at once; asking per
     * candidate would put a query inside the loop that builds the pool.
     */
    @Query("""
            select new com.thenetworkplan.networkplan.crew.dto.PersonInstant(
                       d.person.id, max(d.offDutyAt))
            from DutyPeriod d
            where d.tenantId = :tenantId
              and d.offDutyAt <= :before
            group by d.person.id
            """)
    List<PersonInstant> lastOffDutyBefore(@Param("tenantId") UUID tenantId,
                                          @Param("before") OffsetDateTime before);

    Optional<DutyPeriod> findByTenantIdAndLegIdAndPerson_Id(UUID tenantId, UUID legId, UUID personId);

    /** The last duty that ended before an instant: the base of the rest check. */
    @Query("""
            select d from DutyPeriod d
            where d.tenantId = :tenantId
              and d.person.id = :personId
              and d.offDutyAt <= :before
            order by d.offDutyAt desc
            limit 1
            """)
    DutyPeriod findLastBefore(@Param("tenantId") UUID tenantId,
                              @Param("personId") UUID personId,
                              @Param("before") OffsetDateTime before);
}
