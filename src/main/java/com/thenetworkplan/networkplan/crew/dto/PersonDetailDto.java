package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.util.List;

/**
 * The crew file of one person: identity, qualifications, absences, counters and
 * the duty periods of the last four weeks.
 *
 * <p>Training records are deliberately absent: they belong to the training
 * module and are read from {@code GET /v1/training/persons/{id}}. A module owns
 * its tables and answers for them — the same rule that keeps the dispatch board
 * asking five domains one question each.
 */
public record PersonDetailDto(
        PersonDto person,
        List<QualificationDto> qualifications,
        List<AbsenceDto> absences,
        DutyCountersDto counters,
        List<DutyPeriodDto> recentDuties) implements Serializable {
}
