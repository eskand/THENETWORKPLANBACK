package com.thenetworkplan.networkplan.camo.repository;

import com.thenetworkplan.networkplan.camo.domain.LifeLimitedPart;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LifeLimitedPartRepository extends JpaRepository<LifeLimitedPart, UUID> {

    /**
     * Every part still fitted across the fleet.
     *
     * <p>Removed parts are excluded here and nowhere else: they stay in the
     * table because a disc that came off at its limit is part of the record,
     * but counting them would put retired hardware on the gauge.
     *
     * <p>Not ordered by remaining life: that is derived per row and the
     * database cannot sort on it. The service sorts once it has computed it.
     */
    @Query("""
            select p from LifeLimitedPart p
            join fetch p.aircraft a
            join fetch a.aircraftType
            where p.tenantId = :tenantId
              and p.removedAt is null
            """)
    List<LifeLimitedPart> findFitted(@Param("tenantId") UUID tenantId);

    @Query("""
            select p from LifeLimitedPart p
            join fetch p.aircraft a
            where p.tenantId = :tenantId
              and a.id = :aircraftId
              and p.removedAt is null
            """)
    List<LifeLimitedPart> findByAircraft(@Param("tenantId") UUID tenantId,
                                         @Param("aircraftId") UUID aircraftId);
}
