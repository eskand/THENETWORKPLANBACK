package com.thenetworkplan.networkplan.camo.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One package of work raised against a registration.
 *
 * <p>A due task says what must be done; a work order says where it is being
 * done, by whom, and how far along it is. The distinction matters on the day an
 * aircraft is needed: a C check scheduled at an outstation three days away is
 * not the same availability problem as the same check at home base.
 */
@Entity
@Table(name = "work_orders", schema = "camo")
@Getter
@Setter
public class WorkOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.SCHEDULED;

    @Column(name = "facility")
    private String facility;

    /** Where the shop is. Tells whether the aircraft has to be ferried. */
    @Column(name = "facility_icao")
    private String facilityIcao;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "target_on")
    private LocalDate targetOn;

    /**
     * A target that is not a date and is still information: "ASAP — AOG",
     * "parts on order". Kept beside the date rather than inside it, so an order
     * with no agreed date shows as having none instead of borrowing one.
     */
    @Column(name = "target_note")
    private String targetNote;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "labour_hours")
    private BigDecimal labourHours;

    @Column(name = "remark")
    private String remark;

    /**
     * Past its target and not finished.
     *
     * <p>Derived, never stored: an order cannot stay late after it closes, and
     * an order with no agreed target cannot be late against one.
     */
    public boolean isOverdue(LocalDate on) {
        return closedOn == null && status.isOpen() && targetOn != null && targetOn.isBefore(on);
    }
}
