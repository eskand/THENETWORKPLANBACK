package com.thenetworkplan.networkplan.camo.domain;

/**
 * How close a maintenance task is to its limit.
 *
 * <p>{@code UNKNOWN} is a first-class answer: a task with no date, no hour and
 * no cycle limit on file cannot be said to be in date. The prototype drew such
 * a task as compliant.
 */
public enum DueStatus {

    PLANNED,

    /** Inside the warning window on at least one of its limits. */
    DUE_SOON,

    OVERDUE,

    UNKNOWN;

    public int severity() {
        return switch (this) {
            case PLANNED -> 0;
            case UNKNOWN -> 1;
            case DUE_SOON -> 2;
            case OVERDUE -> 3;
        };
    }

    /** Whether the task stops the aircraft from being dispatched. */
    public boolean grounds() {
        return this == OVERDUE;
    }
}
