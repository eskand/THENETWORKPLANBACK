package com.thenetworkplan.networkplan.camo.domain;

/**
 * Where one work order stands.
 *
 * <p>There is deliberately no {@code OVERDUE}. Late is not a state a work order
 * is put into — it is a target date that has passed while the order is still
 * open, and it stops being true the moment the order closes. The prototype
 * stored it as a status, which meant an order could be marked late and then
 * quietly stay late after being finished.
 */
public enum WorkOrderStatus {
    DRAFT,
    SCHEDULED,
    IN_WORK,
    AWAITING_PARTS,
    CLOSED,
    CANCELLED;

    /** Still consuming the aircraft's availability. */
    public boolean isOpen() {
        return this != CLOSED && this != CANCELLED;
    }
}
