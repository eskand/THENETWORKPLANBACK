package com.thenetworkplan.networkplan.sales.domain;

/** What a quote line charges for. */
public enum QuoteLineKind {
    FLIGHT_HOUR,
    POSITIONING,
    HANDLING,
    FUEL,
    CATERING,
    CREW,
    OVERFLIGHT,
    LANDING,
    PARKING,
    DEICING,
    TAX,
    DISCOUNT,
    OTHER;

    /** A discount is a negative contribution however it is entered. */
    public boolean isDeduction() {
        return this == DISCOUNT;
    }
}
