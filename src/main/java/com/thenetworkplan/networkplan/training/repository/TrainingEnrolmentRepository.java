package com.thenetworkplan.networkplan.training.repository;

import com.thenetworkplan.networkplan.training.domain.TrainingEnrolment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingEnrolmentRepository extends JpaRepository<TrainingEnrolment, UUID> {

    /**
     * Enrolments of a set of sessions, person loaded.
     *
     * <p>One query for the whole calendar: without it, rendering twelve sessions
     * with their attendees costs thirteen statements and the seat count is wrong
     * the moment two people book at once.
     */
    @Query("""
            select e from TrainingEnrolment e
            join fetch e.person
            where e.tenantId = :tenantId
              and e.session.id in :sessionIds
            order by e.person.lastName
            """)
    List<TrainingEnrolment> findBySessionIds(@Param("tenantId") UUID tenantId,
                                             @Param("sessionIds") Collection<UUID> sessionIds);

    @Query("""
            select e from TrainingEnrolment e
            join fetch e.session s
            join fetch s.course
            where e.tenantId = :tenantId
              and e.person.id = :personId
            order by s.startsAt
            """)
    List<TrainingEnrolment> findByPerson(@Param("tenantId") UUID tenantId, @Param("personId") UUID personId);

    @Query("""
            select e from TrainingEnrolment e
            join fetch e.person
            join fetch e.session s
            join fetch s.course
            where e.tenantId = :tenantId
              and e.id = :id
            """)
    Optional<TrainingEnrolment> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    long countBySessionId(UUID sessionId);
}
