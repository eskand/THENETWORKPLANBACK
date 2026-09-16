package com.thenetworkplan.networkplan.tripsupport.domain;

/**
 * Life cycle shared by permit and ground-service requests.
 *
 * <p>The audit found "Submit" only flipped a flag in the prototype: nothing was
 * ever sent. These five states are the contract a real integration has to
 * honour, and {@link #SENT} means a message actually left the product.
 */
public enum RequestStatus {

    /** Drafted, not sent. */
    DRAFT,

    /** Sent to the recipient, no answer yet. */
    SENT,

    /** Recipient confirmed receipt, no reference yet. */
    ACKNOWLEDGED,

    /** Granted, with a reference. */
    CONFIRMED,

    /** Refused: the leg cannot proceed on this basis. */
    REFUSED;

    public boolean isSettled() {
        return this == CONFIRMED;
    }

    public boolean isOutstanding() {
        return this != CONFIRMED;
    }
}
