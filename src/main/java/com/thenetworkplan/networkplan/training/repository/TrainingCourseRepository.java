package com.thenetworkplan.networkplan.training.repository;

import com.thenetworkplan.networkplan.training.domain.TrainingCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingCourseRepository extends JpaRepository<TrainingCourse, UUID> {

    List<TrainingCourse> findByTenantIdOrderByCodeAsc(UUID tenantId);

    List<TrainingCourse> findByTenantIdAndMandatoryTrueOrderByCodeAsc(UUID tenantId);

    Optional<TrainingCourse> findByTenantIdAndCode(UUID tenantId, String code);
}
