package com.thenetworkplan.networkplan.techlog.domain;

/**
 * Life cycle of a tech log page.
 *
 * <p>Signing is the moment the page becomes a maintenance record: it is what
 * feeds the utilisation and therefore the counters, and it happens once.
 */
public enum TechLogStatus {
    OPEN,
    SIGNED,
    CLOSED;

    public boolean isEditable() {
        return this == OPEN;
    }
}
