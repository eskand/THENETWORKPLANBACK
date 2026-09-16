package com.thenetworkplan.networkplan.crewscheduling.service.impl;

import com.thenetworkplan.networkplan.config.CrewProperties;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import com.thenetworkplan.networkplan.crewscheduling.dto.CrewAvailability;
import com.thenetworkplan.networkplan.crewscheduling.service.CrewAvailabilityChecker;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The five findings, worst first.
 *
 * <p>Note what is <em>not</em> here: no flight-duty-period limit, no cumulative
 * check, no acclimatisation. Those are ORO.FTL rules and they belong to the FTL
 * engine of sprint S7. This checker answers a narrower question — may this
 * person be put in this seat at all — and says exactly which fact makes the
 * answer no.
 */
@Component
public class CrewAvailabilityCheckerImpl implements CrewAvailabilityChecker {

    private final CrewDocumentChecker documentChecker;
    private final CrewProperties properties;

    public CrewAvailabilityCheckerImpl(CrewDocumentChecker documentChecker, CrewProperties properties) {
        this.documentChecker = documentChecker;
        this.properties = properties;
    }

    @Override
    public Verdict check(Person person,
                         LocalDate flightDate,
                         String icaoType,
                         Set<String> typeRatings,
                         boolean absent,
                         boolean alreadyAssigned,
                         OffsetDateTime lastOffDutyAt,
                         OffsetDateTime reportAt) {

        Long restMinutes = lastOffDutyAt == null || reportAt == null
                ? null
                : Duration.between(lastOffDutyAt, reportAt).toMinutes();

        if (documentChecker.check(person, flightDate) == DocumentValidity.EXPIRED) {
            return new Verdict(CrewAvailability.DOCUMENTS_EXPIRED,
                    "Licence, medical or recurrent training expired on " + flightDate, restMinutes);
        }
        if (absent) {
            return new Verdict(CrewAvailability.ABSENT,
                    "A declared absence covers " + flightDate, restMinutes);
        }
        if (icaoType != null && !typeRatings.contains(icaoType)) {
            return new Verdict(CrewAvailability.NOT_QUALIFIED,
                    "No valid " + icaoType + " type rating on " + flightDate, restMinutes);
        }
        if (alreadyAssigned) {
            return new Verdict(CrewAvailability.ALREADY_ASSIGNED,
                    "Already holds a seat on an overlapping leg", restMinutes);
        }
        if (restMinutes != null && restMinutes < properties.getMinimumRest().toMinutes()) {
            return new Verdict(CrewAvailability.REST_SHORT,
                    "Rest of " + restMinutes + " min is below the operator minimum of "
                            + properties.getMinimumRest().toMinutes()
                            + " min (operator parameter, not an ORO.FTL.235 verdict)",
                    restMinutes);
        }
        return new Verdict(CrewAvailability.AVAILABLE, null, restMinutes);
    }
}
