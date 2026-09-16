package com.thenetworkplan.networkplan.training.domain;

/**
 * State of one person on one session.
 *
 * <p>{@code ATTENDED} is the only state that produces a training record, and it
 * is produced by the service, not by a screen: the audit found training
 * "completions" that existed only as a rendered tick.
 */
public enum EnrolmentStatus {
    BOOKED,
    ATTENDED,
    NO_SHOW,
    CANCELLED,
    FAILED;

    public boolean producesRecord() {
        return this == ATTENDED;
    }
}
