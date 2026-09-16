package com.thenetworkplan.networkplan.crew.domain;

/**
 * The low-visibility approach a pilot is cleared to fly.
 *
 * <p>The five of the prototype ({@code TNP_OPS_QUALS}), and the operational
 * meaning it prints under its own table: CAT I needs a decision height of at
 * least 200 ft and an RVR of at least 550 m; CAT II, 100 to 200 ft and 300 m;
 * CAT IIIA, below 100 ft and 175 m; CAT IIIB, below 50 ft and 50 to 175 m;
 * CAT IIIC, no decision height and no RVR limit at all.
 *
 * <p>{@link #CAT_I} is the company standard and the floor: it is what a pilot
 * holds when nothing above it was recorded, so it is the one category that
 * needs no training record behind it.
 */
public enum ApproachCategory {
    CAT_I,
    CAT_II,
    CAT_IIIA,
    CAT_IIIB,
    CAT_IIIC;

    /** Above the floor, and therefore a currency that lapses. */
    public boolean isLowVisibility() {
        return this != CAT_I;
    }
}
