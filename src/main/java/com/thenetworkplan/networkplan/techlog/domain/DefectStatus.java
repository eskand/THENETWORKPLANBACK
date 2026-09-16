package com.thenetworkplan.networkplan.techlog.domain;

/**
 * What became of a reported defect.
 *
 * <p>{@code DEFERRED} always points at a MEL item — the database check
 * {@code ck_defect_deferred} refuses the row otherwise. A defect cannot be
 * "deferred" into thin air, which is how open defects disappeared from the
 * prototype.
 */
public enum DefectStatus {
    OPEN,
    DEFERRED,
    CLOSED;

    public boolean isOutstanding() {
        return this != CLOSED;
    }
}
