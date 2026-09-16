package com.thenetworkplan.networkplan.roster.domain;

/**
 * Life cycle of a roster version.
 *
 * <p>Publishing is the moment the roster becomes a commitment to the crew. A
 * published version is never edited: a change opens a new version, so what a
 * crew member was told remains readable afterwards.
 */
public enum RosterStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isEditable() {
        return this == DRAFT;
    }
}
