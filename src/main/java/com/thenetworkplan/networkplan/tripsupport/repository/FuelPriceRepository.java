package com.thenetworkplan.networkplan.tripsupport.repository;

import com.thenetworkplan.networkplan.tripsupport.domain.FuelPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuelPriceRepository extends JpaRepository<FuelPrice, UUID> {

    /**
     * Les tarifs en vigueur a une escale, le jour du vol.
     *
     * <p>« En vigueur » est une condition de date, pas le dernier enregistre :
     * un tarif importe la semaine derniere pour le mois prochain n'est pas le
     * tarif d'aujourd'hui.
     */
    @Query("""
            select f from FuelPrice f
            where f.tenantId = :tenantId
              and f.stationIcao = :station
              and f.effectiveFrom <= :on
              and (f.effectiveTo is null or f.effectiveTo >= :on)
            order by f.effectiveFrom desc, f.supplierName asc
            """)
    List<FuelPrice> findInForce(@Param("tenantId") UUID tenantId,
                                @Param("station") String station,
                                @Param("on") LocalDate on);
}
