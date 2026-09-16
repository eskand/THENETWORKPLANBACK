package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.Component;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComponentRepository extends JpaRepository<Component, UUID> {

    /**
     * The register, with the aircraft fetched.
     *
     * <p>One query rather than one per row: the four component screens each
     * show a registration against every line.
     */
    @Query("""
            select c from Component c
            left join fetch c.aircraft a
            left join fetch a.aircraftType
            where c.tenantId = :tenantId
              and (:category is null or c.category = :category)
            order by c.category, c.name
            """)
    List<Component> findRegister(@Param("tenantId") UUID tenantId,
                                 @Param("category") String category);
}
