package com.thenetworkplan.networkplan.crewscheduling.service;

import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crewscheduling.dto.CrewAvailability;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Whether one crew member can take a seat on one leg.
 *
 * <p>A rule with no repository and no I/O — every input is passed in, already
 * loaded by the service in one query per kind. Same shape as
 * {@code CrewDocumentChecker}, and testable the same way.
 */
public interface CrewAvailabilityChecker {

    /**
     * @param icaoType       aircraft type of the leg
     * @param typeRatings    valid type ratings held on the flight date
     * @param absent         true when a declared absence covers the flight date
     * @param alreadyAssigned true when the person already holds a seat that overlaps
     * @param lastOffDutyAt  end of the last recorded duty, or null when none
     * @param reportAt       reporting time of the leg being crewed
     */
    Verdict check(Person person,
                  LocalDate flightDate,
                  String icaoType,
                  Set<String> typeRatings,
                  boolean absent,
                  boolean alreadyAssigned,
                  OffsetDateTime lastOffDutyAt,
                  OffsetDateTime reportAt);

    /** The verdict and the sentence that explains it, ready to render. */
    record Verdict(CrewAvailability availability, String reason, Long restMinutes) {
    }
}
