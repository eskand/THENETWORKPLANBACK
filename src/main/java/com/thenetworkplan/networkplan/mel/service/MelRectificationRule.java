package com.thenetworkplan.networkplan.mel.service;

import com.thenetworkplan.networkplan.airworthiness.domain.MelCategory;
import java.time.OffsetDateTime;

/**
 * When a deferred item must be rectified.
 *
 * <p>A rule with no I/O, like {@code CrewDocumentChecker} and
 * {@code TrainingValidityRule}. The Part-MEL intervals (A: as stated, B: 3
 * days, C: 10 days, D: 120 days) are the default; the operator MEL line
 * overrides them when it carries its own interval.
 */
public interface MelRectificationRule {

    /**
     * @param libraryDays interval from the operator MEL line, or null
     * @return the instant the item must be cleared by, or null when the
     *         category leaves it to the line itself — which is an answer, not a
     *         gap, and the caller must show it as such
     */
    OffsetDateTime dueAt(MelCategory category, Integer libraryDays, OffsetDateTime raisedAt);
}
