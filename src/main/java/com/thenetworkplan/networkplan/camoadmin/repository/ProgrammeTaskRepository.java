package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.ProgrammeTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgrammeTaskRepository extends JpaRepository<ProgrammeTask, UUID> {

    @Query("""
            select p from ProgrammeTask p
            join fetch p.aircraftType t
            where p.tenantId = :tenantId
              and (:icaoType is null or t.icaoType = :icaoType)
            order by t.icaoType, p.code
            """)
    List<ProgrammeTask> findProgramme(@Param("tenantId") UUID tenantId,
                                      @Param("icaoType") String icaoType);

    @Query("""
            select p from ProgrammeTask p
            join fetch p.aircraftType
            where p.tenantId = :tenantId
              and p.id = :id
            """)
    Optional<ProgrammeTask> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
