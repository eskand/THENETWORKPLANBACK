package com.thenetworkplan.networkplan.crew.service.impl;

import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CrewDocumentCheckerImpl implements CrewDocumentChecker {

    /** A document inside this window is flagged so training can be scheduled. */
    private static final int WARNING_WINDOW_DAYS = 30;

    @Override
    public DocumentValidity check(Person person, LocalDate flightDate) {
        DocumentValidity worst = DocumentValidity.VALID;
        worst = worse(worst, checkDate(person.getLicenceExpiry(), flightDate));
        worst = worse(worst, checkDate(person.getMedicalExpiry(), flightDate));
        worst = worse(worst, checkDate(person.getTrainingExpiry(), flightDate));
        return worst;
    }

    @Override
    public DocumentValidity checkDate(LocalDate expiry, LocalDate flightDate) {
        if (expiry == null) {
            // No date on file is not "valid": it is unknown, and it must show as such.
            return DocumentValidity.UNKNOWN;
        }
        if (expiry.isBefore(flightDate)) {
            return DocumentValidity.EXPIRED;
        }
        if (expiry.isBefore(flightDate.plusDays(WARNING_WINDOW_DAYS))) {
            return DocumentValidity.EXPIRING;
        }
        return DocumentValidity.VALID;
    }

    private DocumentValidity worse(DocumentValidity left, DocumentValidity right) {
        return right.severity() > left.severity() ? right : left;
    }
}
