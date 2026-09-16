package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import java.time.LocalDate;

/**
 * Decides whether a crew member's papers are valid for a given flight date.
 *
 * <p>Its own interface, with no repository and no I/O: a rule this small should
 * be unit-testable without a Spring context, and swappable per authority.
 */
public interface CrewDocumentChecker {

    DocumentValidity check(Person person, LocalDate flightDate);

    /**
     * The same rule applied to a single expiry date.
     *
     * <p>Exposed so that qualifications and training records are judged by the
     * rule that judges licences and medicals, instead of each module growing its
     * own definition of "expiring soon" — which is how the prototype ended up
     * with several answers to one question.
     */
    DocumentValidity checkDate(LocalDate expiry, LocalDate on);
}
