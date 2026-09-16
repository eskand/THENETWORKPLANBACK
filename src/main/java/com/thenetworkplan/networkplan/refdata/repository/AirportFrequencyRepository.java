package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AirportFrequency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportFrequencyRepository extends JpaRepository<AirportFrequency, UUID> {

    List<AirportFrequency> findByAirportIdOrderByServiceAscSortOrderAsc(UUID airportId);
}
