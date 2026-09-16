package com.thenetworkplan.networkplan.roster.domain;

/**
 * What one crew member does on one day, in the operator's own shorthand.
 *
 * <p>The list matches {@code ck_roster_code} in V9, and {@code DutyKind
 * .rosterCode()} is the single translation from a duty period to one of these
 * — a roster cell and a duty period can therefore never disagree.
 */
public enum RosterCode {
    FLT,
    SBY,
    POS,
    TRG,
    OFF,
    LVE,
    SICK,
    OFFICE,
    /** Reserved: a placeholder in a draft, not a commitment. */
    RES,
    /**
     * Dead head: carried as a PASSENGER to reach or leave a posting. On duty,
     * but not part of the crew of the flight — which is what separates it from
     * {@link #POS}, where the person is flying the positioning sector.
     */
    DH,
    /**
     * Simulator. Distinct from {@link #TRG}, which is ground school: an OPC/LPC
     * in the box and a day of classroom do not count the same way towards
     * recency, and folding them together made one of them invisible.
     */
    SIM;

    public boolean isWorking() {
        return this == FLT || this == POS || this == TRG
                || this == OFFICE || this == DH || this == SIM;
    }
}
