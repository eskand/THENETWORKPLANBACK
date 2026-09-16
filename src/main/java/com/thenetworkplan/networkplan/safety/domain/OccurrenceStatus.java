package com.thenetworkplan.networkplan.safety.domain;

/**
 * Where the occurrence is in the safety process.
 *
 * <p>The order matters: a report cannot be closed before it has been assessed,
 * and it cannot be assessed before it has been reviewed. {@link #canClose()}
 * is the single place that rule lives.
 */
public enum OccurrenceStatus {
    REPORTED,
    UNDER_REVIEW,
    RISK_ASSESSED,
    ACTIONS_OPEN,
    CLOSED;

    public boolean canClose() {
        return this == RISK_ASSESSED || this == ACTIONS_OPEN;
    }
}
