package com.thenetworkplan.networkplan.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.service.impl.CrewDocumentCheckerImpl;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule is checked against the flight date, not against today: that is the
 * whole point of passing the date in.
 */
class CrewDocumentCheckerTest {

    private static final LocalDate FLIGHT_DATE = LocalDate.of(2026, 9, 9);

    private final CrewDocumentCheckerImpl checker = new CrewDocumentCheckerImpl();

    @Test
    @DisplayName("all three documents comfortably valid")
    void validWhenEveryDocumentIsFarFromExpiry() {
        Person person = person(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 1));
        assertEquals(DocumentValidity.VALID, checker.check(person, FLIGHT_DATE));
    }

    @Test
    @DisplayName("a medical expiring within thirty days is flagged, not failed")
    void expiringWhenInsideTheWarningWindow() {
        Person person = person(
                LocalDate.of(2027, 1, 1),
                FLIGHT_DATE.plusDays(10),
                LocalDate.of(2027, 1, 1));
        assertEquals(DocumentValidity.EXPIRING, checker.check(person, FLIGHT_DATE));
    }

    @Test
    @DisplayName("a licence expired on the day of the flight is the worst state")
    void expiredBeatsExpiring() {
        Person person = person(
                FLIGHT_DATE.minusDays(1),
                FLIGHT_DATE.plusDays(10),
                LocalDate.of(2027, 1, 1));
        assertEquals(DocumentValidity.EXPIRED, checker.check(person, FLIGHT_DATE));
    }

    @Test
    @DisplayName("a missing expiry date is UNKNOWN, never VALID")
    void missingDateIsUnknown() {
        Person person = person(LocalDate.of(2027, 1, 1), null, LocalDate.of(2027, 1, 1));
        assertEquals(DocumentValidity.UNKNOWN, checker.check(person, FLIGHT_DATE));
    }

    @Test
    @DisplayName("a document valid the day before the flight is expired for that flight")
    void yesterdayIsNotGoodEnough() {
        Person person = person(
                FLIGHT_DATE.plusDays(200),
                FLIGHT_DATE.plusDays(200),
                FLIGHT_DATE.minusDays(1));
        assertEquals(DocumentValidity.EXPIRED, checker.check(person, FLIGHT_DATE));
    }

    private Person person(LocalDate licence, LocalDate medical, LocalDate training) {
        Person person = new Person();
        person.setStaffNo("CPT001");
        person.setFirstName("Mehdi");
        person.setLastName("Bouazizi");
        person.setMainRole(CrewRole.CAPTAIN);
        person.setLicenceExpiry(licence);
        person.setMedicalExpiry(medical);
        person.setTrainingExpiry(training);
        return person;
    }
}
