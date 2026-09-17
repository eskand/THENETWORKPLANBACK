package com.thenetworkplan.networkplan.crew.mapper;

import com.thenetworkplan.networkplan.crew.domain.Absence;
import com.thenetworkplan.networkplan.crew.domain.CrewAssignment;
import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.domain.Qualification;
import com.thenetworkplan.networkplan.crew.dto.AbsenceDto;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.dto.DutyPeriodDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.dto.QualificationDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CrewMapper {

    /**
     * @param onDate day of the flight, against which the three expiry dates are
     *               checked. Passing the flight date rather than "today" is the
     *               point: a licence that expires tomorrow is fine today and not
     *               fine for next week's leg.
     */
    public CrewMemberDto toDto(CrewAssignment assignment, LocalDate onDate, DocumentValidity validity) {
        Person person = assignment.getPerson();
        return new CrewMemberDto(
                assignment.getId(),
                person.getId(),
                person.getStaffNo(),
                person.fullName(),
                assignment.getSeat().name(),
                person.getMainRole().name(),
                assignment.getFtlVerdict().name(),
                assignment.getFtlReason(),
                person.getLicenceExpiry(),
                person.getMedicalExpiry(),
                person.getTrainingExpiry(),
                validity.name(),
                assignment.getDutyStart(),
                assignment.getDutyEnd(),
                assignment.getCheckedInAt(),
                assignment.getCheckedOutAt());
    }

    /**
     * One row of the crew list.
     *
     * <p>The counters and the absence are passed in rather than fetched here: a
     * mapper that queries is a mapper that produces N+1, and the service has
     * already resolved all of them in three aggregates.
     */
    public PersonDto toDto(Person person,
                           DocumentValidity validity,
                           List<String> typeRatings,
                           long blockMinutes7d,
                           long blockMinutes28d,
                           long blockMinutes365d,
                           String absentToday) {
        return new PersonDto(
                person.getId(),
                person.getStaffNo(),
                person.getFirstName(),
                person.getLastName(),
                person.fullName(),
                person.getMainRole().name(),
                person.getBaseIcao(),
                person.getLicenceExpiry(),
                person.getMedicalExpiry(),
                person.getTrainingExpiry(),
                validity.name(),
                typeRatings,
                blockMinutes7d,
                blockMinutes28d,
                blockMinutes365d,
                absentToday,
                person.isActive());
    }

    /** Expects {@code aircraftType} to be loaded: every query left-join-fetches it. */
    public QualificationDto toDto(Qualification qualification, DocumentValidity status) {
        return new QualificationDto(
                qualification.getId(),
                qualification.getKind().name(),
                qualification.getLevel() == null ? null : qualification.getLevel().name(),
                qualification.getAircraftType() == null ? null : qualification.getAircraftType().getIcaoType(),
                qualification.getValidFrom(),
                qualification.getValidTo(),
                status.name(),
                qualification.getReference());
    }

    public AbsenceDto toDto(Absence absence) {
        return new AbsenceDto(
                absence.getId(),
                absence.getPerson().getId(),
                absence.getPerson().fullName(),
                absence.getKind().name(),
                absence.getStartsOn(),
                absence.getEndsOn(),
                absence.getReason());
    }

    public DutyPeriodDto toDto(DutyPeriod duty) {
        return new DutyPeriodDto(
                duty.getId(),
                duty.getPerson().getId(),
                duty.getLegId(),
                duty.getKind().name(),
                duty.getKind().rosterCode(),
                duty.getReportAt(),
                duty.getOffDutyAt(),
                duty.dutyMinutes(),
                duty.getBlockMinutes(),
                duty.getSectors(),
                duty.getRemark());
    }
}
