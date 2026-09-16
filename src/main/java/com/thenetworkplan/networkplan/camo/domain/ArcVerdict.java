package com.thenetworkplan.networkplan.camo.domain;

/**
 * What the airworthiness review certificate says about one registration today.
 *
 * <p>The prototype coloured this in three bands — under 45 days critical, under
 * 90 warning, otherwise fine. Those thresholds are kept, because they are a
 * planning reality: a full review needs a slot and the slot needs booking.
 * What is added is {@link #EXPIRED}, which the prototype folded into its
 * critical band. An aircraft 40 days from renewal is a scheduling problem; an
 * aircraft whose certificate lapsed yesterday may not be dispatched at all, and
 * one colour for both let the second hide inside the first.
 *
 * <p>{@link #NONE} is not an error state either: a registration with no
 * certificate on file is a gap in the record, and saying so is more use than
 * showing a dash.
 */
public enum ArcVerdict {
    EXPIRED,
    CRITICAL,
    DUE_SOON,
    VALID,
    NONE;

    /** Days below which renewal has to be in hand rather than planned. */
    public static final int CRITICAL_DAYS = 45;

    /** Days below which the review has to be on the maintenance plan. */
    public static final int DUE_SOON_DAYS = 90;

    public static ArcVerdict of(Long daysLeft) {
        if (daysLeft == null) {
            return NONE;
        }
        if (daysLeft < 0) {
            return EXPIRED;
        }
        if (daysLeft < CRITICAL_DAYS) {
            return CRITICAL;
        }
        if (daysLeft < DUE_SOON_DAYS) {
            return DUE_SOON;
        }
        return VALID;
    }

    /** The aircraft may not be dispatched on this certificate. */
    public boolean grounds() {
        return this == EXPIRED || this == NONE;
    }
}
