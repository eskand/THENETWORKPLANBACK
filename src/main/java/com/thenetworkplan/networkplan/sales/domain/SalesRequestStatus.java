package com.thenetworkplan.networkplan.sales.domain;

/** Life cycle of a request for quotation. */
public enum SalesRequestStatus {
    NEW,
    QUOTED,
    WON,
    LOST,
    CANCELLED;

    public boolean isOpen() {
        return this == NEW || this == QUOTED;
    }
}
