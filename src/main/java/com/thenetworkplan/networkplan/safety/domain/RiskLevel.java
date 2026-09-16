package com.thenetworkplan.networkplan.safety.domain;

/** Verdict of the 5×5 matrix. */
public enum RiskLevel {
    ACCEPTABLE,
    TOLERABLE,
    UNACCEPTABLE;

    public int severity() {
        return ordinal();
    }
}
