package com.thenetworkplan.networkplan.tripsupport.repository;

import com.thenetworkplan.networkplan.tripsupport.domain.CountryStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryStatusRepository extends JpaRepository<CountryStatus, UUID> {

    @Query("""
            select c from CountryStatus c
            where c.tenantId = :tenantId
              and c.legId = :legId
            order by c.countryIso2
            """)
    List<CountryStatus> findByLeg(@Param("tenantId") UUID tenantId, @Param("legId") UUID legId);
}
