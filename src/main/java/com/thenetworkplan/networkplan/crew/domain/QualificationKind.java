package com.thenetworkplan.networkplan.crew.domain;

/**
 * Nature of a crew qualification.
 *
 * <p>The list is the one the check constraint {@code ck_qualification_kind}
 * enforces in V9: the enum and the database say the same thing, and the database
 * is the one that cannot be bypassed.
 */
public enum QualificationKind {

    /** Rating on one aircraft type: the only kind that carries a type. */
    TYPE_RATING,

    LINE_CHECK,
    OPC,
    LPC,
    SEP,
    CRM,
    DANGEROUS_GOODS,
    ETOPS,

    /** Low visibility operations, CAT II / III. */
    LVO,

    ROUTE_COMPETENCE;

    /** True when the qualification is meaningless without an aircraft type. */
    public boolean requiresAircraftType() {
        return this == TYPE_RATING || this == ETOPS;
    }
}
