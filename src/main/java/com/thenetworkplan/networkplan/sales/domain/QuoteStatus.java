package com.thenetworkplan.networkplan.sales.domain;

/** Life cycle of a quote. A sent quote is never edited: a new version is issued. */
public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REFUSED,
    EXPIRED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isDecided() {
        return this == ACCEPTED || this == REFUSED;
    }
}
