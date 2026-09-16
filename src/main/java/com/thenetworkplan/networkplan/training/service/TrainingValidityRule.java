package com.thenetworkplan.networkplan.training.service;

import com.thenetworkplan.networkplan.training.domain.TrainingCourse;
import java.time.LocalDate;

/**
 * When a completed course lapses.
 *
 * <p>Its own interface, no repository, no I/O — the shape of
 * {@code CrewDocumentChecker} and for the same reason: a rule this small must be
 * unit-testable without a Spring context, and an operator whose authority
 * counts validity differently (calendar month end rather than day for day)
 * swaps the implementation and nothing else.
 */
public interface TrainingValidityRule {

    /**
     * @return the day the certificate stops being valid, or null when the
     *         course never expires. Null is a real answer here, not a gap.
     */
    LocalDate validUntil(TrainingCourse course, LocalDate completedOn);
}
