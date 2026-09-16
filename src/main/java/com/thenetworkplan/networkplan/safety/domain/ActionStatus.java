package com.thenetworkplan.networkplan.safety.domain;

/** Life cycle of a safety action. */
public enum ActionStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isOutstanding() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
