package com.thenetworkplan.networkplan.training.repository;

import com.thenetworkplan.networkplan.training.domain.TrainingSession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    /** Course and instructor fetch-joined: the mapper reads both on every row. */
    @Query("""
            select s from TrainingSession s
            join fetch s.course
            left join fetch s.instructor
            where s.tenantId = :tenantId
              and s.startsAt >= :from
              and s.startsAt < :to
            order by s.startsAt
            """)
    List<TrainingSession> findInWindow(@Param("tenantId") UUID tenantId,
                                       @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to);

    @Query("""
            select s from TrainingSession s
            join fetch s.course
            left join fetch s.instructor
            where s.tenantId = :tenantId
              and s.id = :id
            """)
    Optional<TrainingSession> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
