package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.crew.dto.AbsenceDto;
import com.thenetworkplan.networkplan.crew.dto.CrewExpiryDto;
import com.thenetworkplan.networkplan.crew.dto.OpsQualificationDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDetailDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.dto.QualificationDto;
import com.thenetworkplan.networkplan.crew.dto.SaveAbsenceCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveApproachCategoryCommand;
import com.thenetworkplan.networkplan.crew.dto.SavePersonCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveQualificationCommand;
import java.util.List;
import java.util.UUID;

/**
 * DOM4 — the crew files themselves (Crew Management screen).
 *
 * <p>Same shape as {@code AircraftService}: every method takes the tenant
 * explicitly rather than reading the thread-local, so a caller that fans out on
 * virtual threads can use it without carrying a request context.
 */
public interface CrewPeopleService {

    /**
     * The crew list, with counters and document state already resolved.
     *
     * @param role optional filter on the main role
     * @param search optional case-insensitive match on staff number or name
     */
    List<PersonDto> findAll(UUID tenantId, String role, String search, boolean activeOnly);

    PersonDetailDto findOne(UUID tenantId, UUID personId);

    PersonDto create(UUID tenantId, SavePersonCommand command);

    PersonDto update(UUID tenantId, UUID personId, SavePersonCommand command);

    /**
     * The expiry wall: licences, medicals, recurrent training and every
     * qualification lapsing inside the horizon, worst first.
     *
     * <p>One list, not two: the OCC and the crew planner ask the same question
     * of a licence and of a type rating, and answering it twice would mean two
     * definitions of "expiring soon".
     */
    List<CrewExpiryDto> findExpiring(UUID tenantId, int horizonDays);

    QualificationDto addQualification(UUID tenantId, UUID personId, SaveQualificationCommand command);

    /** The flight crew and their low-visibility approach category, CAT I by default. */
    List<OpsQualificationDto> findOpsQualifications(UUID tenantId);

    /** Grades one pilot. Anything above CAT I needs an expiry date. */
    OpsQualificationDto setApproachCategory(UUID tenantId, UUID personId, SaveApproachCategoryCommand command);

    void removeQualification(UUID tenantId, UUID qualificationId);

    AbsenceDto addAbsence(UUID tenantId, UUID personId, SaveAbsenceCommand command);

    void removeAbsence(UUID tenantId, UUID absenceId);
}
