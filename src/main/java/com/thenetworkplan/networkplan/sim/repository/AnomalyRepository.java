package com.thenetworkplan.networkplan.sim.repository;

import com.thenetworkplan.networkplan.sim.domain.Anomaly;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {

    @Query("""
            select a from Anomaly a
            where a.scenario.id = :scenarioId
            order by a.reference
            """)
    List<Anomaly> findByScenario(@Param("scenarioId") UUID scenarioId);
}
