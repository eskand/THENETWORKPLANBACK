package com.thenetworkplan.networkplan.camoadmin.domain;

/** What one registration has done with one directive. */
public enum DirectiveStatus {
    OPEN,
    COMPLIED,
    NOT_APPLICABLE,
    DEFERRED;

    public boolean isOutstanding() {
        return this == OPEN || this == DEFERRED;
    }
}
