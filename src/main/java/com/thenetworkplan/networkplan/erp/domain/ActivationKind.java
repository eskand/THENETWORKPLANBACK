package com.thenetworkplan.networkplan.erp.domain;

/** Why the plan was activated. */
public enum ActivationKind {
    EXERCISE,
    REAL,
    STANDBY;

    public boolean isDrill() {
        return this == EXERCISE;
    }
}
