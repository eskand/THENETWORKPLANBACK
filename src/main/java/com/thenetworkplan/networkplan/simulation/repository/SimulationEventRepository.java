package com.thenetworkplan.networkplan.simulation.repository;

import com.thenetworkplan.networkplan.simulation.domain.SimulationEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimulationEventRepository extends JpaRepository<SimulationEvent, UUID> {

    /** Every inject of a set of scenarios, in one statement. */
    @Query("""
            select e from SimulationEvent e
            where e.tenantId = :tenantId
              and e.scenario.id in :scenarioIds
            order by e.scenario.id, e.sequenceNo
            """)
    List<SimulationEvent> findByScenarioIds(@Param("tenantId") UUID tenantId,
                                            @Param("scenarioIds") Collection<UUID> scenarioIds);
}
