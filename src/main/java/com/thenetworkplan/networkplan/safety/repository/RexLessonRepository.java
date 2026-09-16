package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.RexLesson;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RexLessonRepository extends JpaRepository<RexLesson, UUID> {

    List<RexLesson> findByRexIdOrderBySortOrder(UUID rexId);

    List<RexLesson> findAllByOrderBySortOrder();
}
