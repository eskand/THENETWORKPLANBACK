package com.thenetworkplan.networkplan.camo.domain;

/**
 * On what ground an airworthiness review certificate was issued.
 *
 * <p>The three are not interchangeable. A full review is a physical survey of
 * the aircraft and its records; an extension prolongs an existing certificate
 * without one, and can only be granted twice in a row; a recommendation is what
 * the CAMO sends the authority when it is not itself entitled to issue.
 * Recording only the date would hide which of the three happened, and a
 * third consecutive extension is a finding.
 */
public enum ReviewBasis {
    FULL,
    EXTENSION,
    RECOMMENDATION;

    /** An extension rests on an earlier full review rather than on a new one. */
    public boolean isExtension() {
        return this == EXTENSION;
    }
}
