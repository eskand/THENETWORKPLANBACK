package com.thenetworkplan.networkplan.camo.repository;

import com.thenetworkplan.networkplan.camo.domain.WorkOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    /**
     * Orders still running, fleet-wide, nearest target first.
     *
     * <p>Orders with no agreed target sort last rather than first: an order
     * without a date is not urgent by default, it is undated, and putting it at
     * the head of the list would read as the opposite.
     */
    @Query("""
            select w from WorkOrder w
            join fetch w.aircraft a
            where w.tenantId = :tenantId
              and w.closedOn is null
              and w.status not in (
                  com.thenetworkplan.networkplan.camo.domain.WorkOrderStatus.CLOSED,
                  com.thenetworkplan.networkplan.camo.domain.WorkOrderStatus.CANCELLED)
            order by w.targetOn asc nulls last, w.orderNo
            """)
    List<WorkOrder> findOpen(@Param("tenantId") UUID tenantId);

    @Query("""
            select w from WorkOrder w
            join fetch w.aircraft a
            where w.tenantId = :tenantId
              and a.id = :aircraftId
            order by w.closedOn asc nulls first, w.targetOn asc nulls last
            """)
    List<WorkOrder> findByAircraft(@Param("tenantId") UUID tenantId,
                                   @Param("aircraftId") UUID aircraftId);
}
