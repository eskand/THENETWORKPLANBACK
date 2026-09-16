package com.thenetworkplan.networkplan.sim.repository;

import com.thenetworkplan.networkplan.sim.domain.ScenarioLeg;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScenarioLegRepository extends JpaRepository<ScenarioLeg, UUID> {

    @Query("""
            select l from ScenarioLeg l
            where l.scenario.id = :scenarioId
            order by l.registration, l.std
            """)
    List<ScenarioLeg> findByScenario(@Param("scenarioId") UUID scenarioId);
}
