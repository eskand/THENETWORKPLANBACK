package com.thenetworkplan.networkplan.tripsupport.domain;

/** Verdict for one overflown or landed-in state. */
public enum CountryPermitStatus {

    /** An air services agreement or exemption covers the flight. */
    NOT_REQUIRED,

    /** A permit must be obtained. */
    PERMIT_REQUIRED,

    /**
     * No instrument is known for this pair of states.
     *
     * <p>This is the value that keeps the product honest: the prototype answered
     * "confirmed" on a coin flip. Not knowing is a result, and it is shown.
     */
    NO_INSTRUMENT_KNOWN
}
