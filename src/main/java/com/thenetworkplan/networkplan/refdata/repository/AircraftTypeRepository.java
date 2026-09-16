package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftTypeRepository extends JpaRepository<AircraftType, UUID> {

    Optional<AircraftType> findByIcaoType(String icaoType);
}
