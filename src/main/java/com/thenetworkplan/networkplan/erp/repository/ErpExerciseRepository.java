package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpExercise;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpExerciseRepository extends JpaRepository<ErpExercise, UUID> {

    /**
     * The register, most recent first.
     *
     * <p>Planned exercises sort in with the rest rather than into their own
     * list: what matters on the console is when the plan was last tried and
     * when it will be tried next, read in one column.
     */
    List<ErpExercise> findByTenantIdOrderByHeldOnDesc(UUID tenantId);
}
