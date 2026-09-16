package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AirportNote;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AirportNoteRepository extends JpaRepository<AirportNote, UUID> {

    @Query("""
            select n from AirportNote n
            where n.airport.id in :airportIds
            order by n.airport.id, n.severity desc, n.kind
            """)
    List<AirportNote> findByAirportIds(@Param("airportIds") Collection<UUID> airportIds);
}
