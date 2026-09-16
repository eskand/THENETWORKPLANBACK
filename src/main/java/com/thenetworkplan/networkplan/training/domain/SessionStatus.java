package com.thenetworkplan.networkplan.training.domain;

/** Life cycle of a scheduled course session. */
public enum SessionStatus {
    PLANNED,
    RUNNING,
    COMPLETED,
    CANCELLED;

    /** A session only accepts enrolments while it is still ahead. */
    public boolean acceptsEnrolment() {
        return this == PLANNED;
    }
}
