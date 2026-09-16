package com.thenetworkplan.networkplan.tripsupport.repository;

import com.thenetworkplan.networkplan.tripsupport.domain.Supplier;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /**
     * Suppliers of a set of stations, in one statement.
     *
     * <p>The board shows the options for every station of the day; asking per
     * station would put a query inside the loop that builds it.
     */
    @Query("""
            select s from Supplier s
            where s.tenantId = :tenantId
              and s.active = true
              and s.stationIcao in :stations
            order by s.stationIcao, s.serviceType, s.preferred desc, s.name
            """)
    List<Supplier> findForStations(@Param("tenantId") UUID tenantId,
                                   @Param("stations") Collection<String> stations);

    @Query("""
            select s from Supplier s
            where s.tenantId = :tenantId
              and (:station is null or s.stationIcao = :station)
              and s.active = true
            order by s.stationIcao, s.serviceType, s.preferred desc, s.name
            """)
    List<Supplier> findDirectory(@Param("tenantId") UUID tenantId, @Param("station") String station);
}
