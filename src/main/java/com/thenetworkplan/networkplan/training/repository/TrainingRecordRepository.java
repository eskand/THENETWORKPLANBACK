package com.thenetworkplan.networkplan.training.repository;

import com.thenetworkplan.networkplan.training.domain.TrainingRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingRecordRepository extends JpaRepository<TrainingRecord, UUID> {

    /**
     * Every record of the tenant, person and course loaded, most recent first.
     *
     * <p>The compliance matrix keeps the newest record per (person, course) and
     * drops the rest. Doing that in one query and one pass is cheaper than a
     * correlated {@code max(completed_on)} sub-select per cell, and the volume is
     * bounded by the crew multiplied by the catalogue — hundreds of rows, not
     * millions.
     */
    @Query("""
            select r from TrainingRecord r
            join fetch r.person p
            join fetch r.course
            where r.tenantId = :tenantId
              and (:activeOnly = false or p.active = true)
            order by r.completedOn desc
            """)
    List<TrainingRecord> findAllForMatrix(@Param("tenantId") UUID tenantId,
                                          @Param("activeOnly") boolean activeOnly);

    @Query("""
            select r from TrainingRecord r
            join fetch r.course
            left join fetch r.instructor
            where r.tenantId = :tenantId
              and r.person.id = :personId
            order by r.completedOn desc
            """)
    List<TrainingRecord> findByPerson(@Param("tenantId") UUID tenantId, @Param("personId") UUID personId);
}
