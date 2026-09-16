package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.Runway;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunwayRepository extends JpaRepository<Runway, UUID> {

    @Query("""
            select r from Runway r
            where r.airport.id in :airportIds
            order by r.airport.id, r.lengthFt desc
            """)
    List<Runway> findByAirportIds(@Param("airportIds") Collection<UUID> airportIds);
}
