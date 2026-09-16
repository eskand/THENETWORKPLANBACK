package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AirportService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportServiceRepository extends JpaRepository<AirportService, UUID> {

    List<AirportService> findByAirportIdOrderByServiceTypeAscNameAsc(UUID airportId);
}
