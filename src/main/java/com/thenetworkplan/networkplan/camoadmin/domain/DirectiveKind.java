package com.thenetworkplan.networkplan.camoadmin.domain;

/** Nature of the instruction. Only an AD is mandatory by itself. */
public enum DirectiveKind {
    AD,
    SB,
    STC,
    MOD;

    public boolean isMandatory() {
        return this == AD;
    }
}
