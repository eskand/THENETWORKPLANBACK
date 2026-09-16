package com.thenetworkplan.networkplan.crew.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.Absence;
import com.thenetworkplan.networkplan.crew.domain.AbsenceKind;
import com.thenetworkplan.networkplan.crew.domain.ApproachCategory;
import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.domain.Qualification;
import com.thenetworkplan.networkplan.crew.domain.QualificationKind;
import com.thenetworkplan.networkplan.crew.domain.QualificationLevel;
import com.thenetworkplan.networkplan.crew.dto.AbsenceDto;
import com.thenetworkplan.networkplan.crew.dto.CrewExpiryDto;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.dto.DutyCountersDto;
import com.thenetworkplan.networkplan.crew.dto.DutyPeriodDto;
import com.thenetworkplan.networkplan.crew.dto.OpsQualificationDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDetailDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.dto.PersonMinutes;
import com.thenetworkplan.networkplan.crew.dto.QualificationDto;
import com.thenetworkplan.networkplan.crew.dto.SaveAbsenceCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveApproachCategoryCommand;
import com.thenetworkplan.networkplan.crew.dto.SavePersonCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveQualificationCommand;
import com.thenetworkplan.networkplan.crew.mapper.CrewMapper;
import com.thenetworkplan.networkplan.crew.repository.AbsenceRepository;
import com.thenetworkplan.networkplan.crew.repository.DutyPeriodRepository;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.crew.repository.QualificationRepository;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.refdata.repository.AircraftTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crew Management.
 *
 * <p>Reading the list costs five statements whatever the size of the crew: the
 * people, their type ratings, the absences overlapping today, and three
 * aggregates of {@code crew.duty_periods}. Nothing is queried inside a loop.
 */
@Service
@Transactional(readOnly = true)
public class CrewPeopleServiceImpl implements CrewPeopleService {

    private final PersonRepository personRepository;
    private final QualificationRepository qualificationRepository;
    private final AbsenceRepository absenceRepository;
    private final DutyPeriodRepository dutyPeriodRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final CrewDocumentChecker documentChecker;
    private final CrewMapper mapper;

    public CrewPeopleServiceImpl(PersonRepository personRepository,
                                 QualificationRepository qualificationRepository,
                                 AbsenceRepository absenceRepository,
                                 DutyPeriodRepository dutyPeriodRepository,
                                 AircraftTypeRepository aircraftTypeRepository,
                                 CrewDocumentChecker documentChecker,
                                 CrewMapper mapper) {
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
        this.absenceRepository = absenceRepository;
        this.dutyPeriodRepository = dutyPeriodRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.documentChecker = documentChecker;
        this.mapper = mapper;
    }

    @Override
    public List<PersonDto> findAll(UUID tenantId, String role, String search, boolean activeOnly) {
        CrewRole crewRole = parseRole(role);
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase() + "%";

        List<Person> people = personRepository.search(tenantId, crewRole, pattern, activeOnly);
        if (people.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = people.stream().map(Person::getId).toList();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Map<UUID, List<String>> ratings = typeRatingsByPerson(tenantId, ids);
        Map<UUID, String> absentToday = absenceRepository.findOverlapping(tenantId, ids, today, today).stream()
                .collect(Collectors.toMap(a -> a.getPerson().getId(), a -> a.getKind().name(), (first, second) -> first));

        Map<UUID, Long> block7 = minutesByPerson(tenantId, now.minusDays(7));
        Map<UUID, Long> block28 = minutesByPerson(tenantId, now.minusDays(28));
        Map<UUID, Long> block365 = minutesByPerson(tenantId, now.minusDays(365));

        return people.stream()
                .map(person -> mapper.toDto(
                        person,
                        documentChecker.check(person, today),
                        ratings.getOrDefault(person.getId(), List.of()),
                        block7.getOrDefault(person.getId(), 0L),
                        block28.getOrDefault(person.getId(), 0L),
                        block365.getOrDefault(person.getId(), 0L),
                        absentToday.get(person.getId())))
                .toList();
    }

    @Override
    public PersonDetailDto findOne(UUID tenantId, UUID personId) {
        Person person = require(tenantId, personId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Qualification> qualifications = qualificationRepository.findByPerson(tenantId, personId);
        List<QualificationDto> qualificationDtos = qualifications.stream()
                .map(q -> mapper.toDto(q, documentChecker.checkDate(q.getValidTo(), today)))
                .toList();

        List<AbsenceDto> absences = absenceRepository.findByPerson(tenantId, personId).stream()
                .map(mapper::toDto)
                .toList();

        List<DutyPeriod> duties = dutyPeriodRepository.findByPersonInWindow(
                tenantId, personId, now.minusDays(28), now.plusDays(14));
        List<DutyPeriodDto> dutyDtos = duties.stream().map(mapper::toDto).toList();

        List<String> ratings = qualifications.stream()
                .filter(q -> q.getKind() == QualificationKind.TYPE_RATING && q.getAircraftType() != null)
                .map(q -> q.getAircraftType().getIcaoType())
                .sorted()
                .toList();

        PersonDto dto = mapper.toDto(
                person,
                documentChecker.check(person, today),
                ratings,
                countersFor(duties, now.minusDays(7), true),
                countersFor(duties, now.minusDays(28), true),
                // The 365-day cumulative cannot be read off the 28-day window: it is
                // the only figure of the detail page that needs its own aggregate.
                minutesByPerson(tenantId, now.minusDays(365)).getOrDefault(personId, 0L),
                absences.stream()
                        .filter(a -> !today.isBefore(a.startsOn()) && !today.isAfter(a.endsOn()))
                        .map(AbsenceDto::kind)
                        .findFirst()
                        .orElse(null));

        DutyPeriod last = dutyPeriodRepository.findLastBefore(tenantId, personId, now);
        DutyCountersDto counters = new DutyCountersDto(
                personId,
                dto.blockMinutes7d(),
                dto.blockMinutes28d(),
                dto.blockMinutes365d(),
                countersFor(duties, now.minusDays(7), false),
                countersFor(duties, now.minusDays(28), false),
                last == null ? null : last.getOffDutyAt(),
                now);

        return new PersonDetailDto(dto, qualificationDtos, absences, counters, dutyDtos);
    }

    @Override
    @Transactional
    public PersonDto create(UUID tenantId, SavePersonCommand command) {
        personRepository.findByTenantIdAndStaffNo(tenantId, command.staffNo()).ifPresent(existing -> {
            throw new BusinessRuleException("STAFF_NO_TAKEN",
                    "Staff number " + command.staffNo() + " already belongs to " + existing.fullName());
        });
        Person person = new Person();
        person.setTenantId(tenantId);
        person.setStaffNo(command.staffNo().trim().toUpperCase());
        apply(person, command);
        return toListDto(tenantId, personRepository.save(person));
    }

    @Override
    @Transactional
    public PersonDto update(UUID tenantId, UUID personId, SavePersonCommand command) {
        Person person = require(tenantId, personId);
        apply(person, command);
        return toListDto(tenantId, personRepository.save(person));
    }

    /**
     * Licences, medicals, recurrent training and qualifications in one list.
     *
     * <p>Two statements: the active crew, and the qualifications lapsing
     * before the horizon. The three document dates come from the person row
     * that is already loaded, so they cost nothing more.
     */
    @Override
    public List<CrewExpiryDto> findExpiring(UUID tenantId, int horizonDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate horizon = today.plusDays(horizonDays);
        List<CrewExpiryDto> rows = new ArrayList<>();

        for (Person person : personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId)) {
            addDocument(rows, person, "LICENCE", person.getLicenceExpiry(), today, horizon);
            addDocument(rows, person, "MEDICAL", person.getMedicalExpiry(), today, horizon);
            addDocument(rows, person, "TRAINING", person.getTrainingExpiry(), today, horizon);
        }

        for (Qualification qualification : qualificationRepository.findExpiringBefore(tenantId, horizon)) {
            Person person = qualification.getPerson();
            rows.add(new CrewExpiryDto(
                    person.getId(),
                    person.getStaffNo(),
                    person.fullName(),
                    person.getMainRole().name(),
                    person.getBaseIcao(),
                    qualification.getKind().name(),
                    subjectOf(qualification),
                    qualification.getValidTo(),
                    qualification.getValidTo() == null
                            ? null
                            : ChronoUnit.DAYS.between(today, qualification.getValidTo()),
                    documentChecker.checkDate(qualification.getValidTo(), today).name(),
                    qualification.getReference()));
        }

        // Worst first, then soonest: an expired licence outranks a rating that
        // lapses in eighty days, whatever the alphabet says.
        rows.sort(Comparator
                .comparingInt((CrewExpiryDto row) -> DocumentValidity.valueOf(row.status()).severity())
                .reversed()
                .thenComparing(row -> row.expiresOn() == null ? LocalDate.MAX : row.expiresOn()));
        return rows;
    }

    /** A document is added only when it lapses inside the horizon, or is missing. */
    private void addDocument(List<CrewExpiryDto> rows,
                             Person person,
                             String kind,
                             LocalDate expiry,
                             LocalDate today,
                             LocalDate horizon) {
        if (expiry != null && expiry.isAfter(horizon)) {
            return;
        }
        rows.add(new CrewExpiryDto(
                person.getId(),
                person.getStaffNo(),
                person.fullName(),
                person.getMainRole().name(),
                person.getBaseIcao(),
                kind,
                null,
                expiry,
                expiry == null ? null : ChronoUnit.DAYS.between(today, expiry),
                documentChecker.checkDate(expiry, today).name(),
                null));
    }

    private String subjectOf(Qualification qualification) {
        if (qualification.getAircraftType() != null) {
            return qualification.getAircraftType().getIcaoType();
        }
        return qualification.getLevel() == null ? null : qualification.getLevel().name();
    }

    @Override
    @Transactional
    public QualificationDto addQualification(UUID tenantId, UUID personId, SaveQualificationCommand command) {
        Person person = require(tenantId, personId);
        QualificationKind kind = parseEnum(QualificationKind.class, command.kind(), "QUALIFICATION_KIND_UNKNOWN");

        AircraftType type = null;
        if (command.icaoType() != null && !command.icaoType().isBlank()) {
            type = aircraftTypeRepository.findByIcaoType(command.icaoType().trim().toUpperCase())
                    .orElseThrow(() -> ResourceNotFoundException.of("Aircraft type", command.icaoType()));
        }
        if (kind.requiresAircraftType() && type == null) {
            throw new BusinessRuleException("QUALIFICATION_TYPE_REQUIRED",
                    kind + " has no meaning without an aircraft type");
        }

        Qualification qualification = new Qualification();
        qualification.setTenantId(tenantId);
        qualification.setPerson(person);
        qualification.setAircraftType(type);
        qualification.setKind(kind);
        qualification.setLevel(command.level() == null || command.level().isBlank()
                ? null
                : parseEnum(QualificationLevel.class, command.level(), "QUALIFICATION_LEVEL_UNKNOWN"));
        qualification.setValidFrom(command.validFrom());
        qualification.setValidTo(command.validTo());
        qualification.setReference(command.reference());

        Qualification saved = qualificationRepository.save(qualification);
        return mapper.toDto(saved, documentChecker.checkDate(saved.getValidTo(), LocalDate.now(ZoneOffset.UTC)));
    }

    /**
     * The flight crew and their low-visibility approach category.
     *
     * <p>Cabin crew are left out: an approach category describes who may fly
     * the approach, and listing people it cannot apply to would invite somebody
     * to set one.
     *
     * <p>A pilot with no LVO qualification comes back as CAT I, ungraded. That
     * is the company standard the prototype states in its own wording, and it
     * is a real answer rather than a blank.
     */
    @Override
    public List<OpsQualificationDto> findOpsQualifications(UUID tenantId) {
        List<Person> pilots = personRepository
                .findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId).stream()
                .filter(person -> person.getMainRole() == CrewRole.CAPTAIN
                        || person.getMainRole() == CrewRole.FIRST_OFFICER)
                .toList();
        if (pilots.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = pilots.stream().map(Person::getId).toList();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Map<UUID, List<String>> ratings = typeRatingsByPerson(tenantId, ids);
        Map<UUID, Qualification> lvo = qualificationRepository.findByPersonIds(tenantId, ids).stream()
                .filter(qualification -> qualification.getKind() == QualificationKind.LVO)
                .collect(Collectors.toMap(qualification -> qualification.getPerson().getId(),
                        qualification -> qualification, (first, second) -> first));

        return pilots.stream().map(person -> {
            Qualification qualification = lvo.get(person.getId());
            boolean graded = qualification != null && qualification.getApproachCategory() != null;
            return new OpsQualificationDto(
                    person.getId(),
                    person.getStaffNo(),
                    person.fullName(),
                    person.getMainRole().name(),
                    ratings.getOrDefault(person.getId(), List.of()),
                    graded ? qualification.getApproachCategory() : ApproachCategory.CAT_I.name(),
                    graded,
                    qualification == null ? null : qualification.getValidFrom(),
                    qualification == null ? null : qualification.getValidTo(),
                    // CAT I needs no currency; above it, the training lapses.
                    graded && qualification.getApproachCategory().equals(ApproachCategory.CAT_I.name())
                            ? "VALID"
                            : qualification == null ? "VALID"
                                    : documentChecker.checkDate(qualification.getValidTo(), today).name(),
                    qualification == null ? null : qualification.getReference());
        }).toList();
    }

    /**
     * Grades a pilot's approach category.
     *
     * <p>Creates the LVO qualification the first time, updates it afterwards.
     * Going back down to CAT I does not delete the row: the training was done,
     * its reference and its dates are part of the file, and deleting them to
     * make a dropdown tidy would lose a record.
     */
    @Override
    @Transactional
    public OpsQualificationDto setApproachCategory(UUID tenantId, UUID personId,
                                                   SaveApproachCategoryCommand command) {
        Person person = personRepository.findByTenantIdAndId(tenantId, personId)
                .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
        if (person.getMainRole() != CrewRole.CAPTAIN && person.getMainRole() != CrewRole.FIRST_OFFICER) {
            throw new BusinessRuleException("APPROACH_CATEGORY_NOT_FLIGHT_CREW",
                    person.fullName() + " is not flight crew; an approach category does not apply");
        }

        ApproachCategory category = parseEnum(ApproachCategory.class, command.category(),
                "APPROACH_CATEGORY_UNKNOWN");

        // A category above CAT I is a training with an end date; the rule is
        // stated once, here, rather than trusted to whoever fills the form.
        if (category != ApproachCategory.CAT_I && command.validTo() == null) {
            throw new BusinessRuleException("APPROACH_CATEGORY_NEEDS_EXPIRY",
                    category.name().replace('_', ' ') + " is a currency, not an attribute; it needs an expiry date");
        }

        Qualification qualification = qualificationRepository.findByPerson(tenantId, personId).stream()
                .filter(row -> row.getKind() == QualificationKind.LVO)
                .findFirst()
                .orElseGet(() -> {
                    Qualification created = new Qualification();
                    created.setTenantId(tenantId);
                    created.setPerson(person);
                    created.setKind(QualificationKind.LVO);
                    return created;
                });

        qualification.setApproachCategory(category.name());
        qualification.setValidFrom(command.validFrom());
        qualification.setValidTo(command.validTo());
        if (command.reference() != null && !command.reference().isBlank()) {
            qualification.setReference(command.reference().trim());
        }
        qualificationRepository.save(qualification);

        return findOpsQualifications(tenantId).stream()
                .filter(row -> row.personId().equals(personId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
    }

    @Override
    @Transactional
    public void removeQualification(UUID tenantId, UUID qualificationId) {
        Qualification qualification = qualificationRepository.findById(qualificationId)
                .filter(q -> tenantId.equals(q.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Qualification", qualificationId));
        qualificationRepository.delete(qualification);
    }

    @Override
    @Transactional
    public AbsenceDto addAbsence(UUID tenantId, UUID personId, SaveAbsenceCommand command) {
        Person person = require(tenantId, personId);
        if (command.endsOn().isBefore(command.startsOn())) {
            throw new BusinessRuleException("ABSENCE_WINDOW_INVALID",
                    "An absence cannot end before it starts");
        }
        Absence absence = new Absence();
        absence.setTenantId(tenantId);
        absence.setPerson(person);
        absence.setKind(parseEnum(AbsenceKind.class, command.kind(), "ABSENCE_KIND_UNKNOWN"));
        absence.setStartsOn(command.startsOn());
        absence.setEndsOn(command.endsOn());
        absence.setReason(command.reason());
        return mapper.toDto(absenceRepository.save(absence));
    }

    @Override
    @Transactional
    public void removeAbsence(UUID tenantId, UUID absenceId) {
        Absence absence = absenceRepository.findById(absenceId)
                .filter(a -> tenantId.equals(a.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Absence", absenceId));
        absenceRepository.delete(absence);
    }

    // ----------------------------------------------------------------
    //  helpers
    // ----------------------------------------------------------------

    private Person require(UUID tenantId, UUID personId) {
        return personRepository.findByTenantIdAndId(tenantId, personId)
                .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
    }

    private void apply(Person person, SavePersonCommand command) {
        person.setFirstName(command.firstName().trim());
        person.setLastName(command.lastName().trim());
        person.setMainRole(parseEnum(CrewRole.class, command.mainRole(), "CREW_ROLE_UNKNOWN"));
        person.setBaseIcao(command.baseIcao());
        person.setLicenceExpiry(command.licenceExpiry());
        person.setMedicalExpiry(command.medicalExpiry());
        person.setTrainingExpiry(command.trainingExpiry());
        if (command.active() != null) {
            person.setActive(command.active());
        }
    }

    /** A freshly written person has no history yet: the counters read zero, truthfully. */
    private PersonDto toListDto(UUID tenantId, Person person) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<String> ratings = typeRatingsByPerson(tenantId, List.of(person.getId()))
                .getOrDefault(person.getId(), List.of());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return mapper.toDto(
                person,
                documentChecker.check(person, today),
                ratings,
                minutesByPerson(tenantId, now.minusDays(7)).getOrDefault(person.getId(), 0L),
                minutesByPerson(tenantId, now.minusDays(28)).getOrDefault(person.getId(), 0L),
                minutesByPerson(tenantId, now.minusDays(365)).getOrDefault(person.getId(), 0L),
                null);
    }

    private Map<UUID, List<String>> typeRatingsByPerson(UUID tenantId, List<UUID> personIds) {
        Map<UUID, List<String>> ratings = new HashMap<>();
        for (Qualification qualification : qualificationRepository.findByPersonIds(tenantId, personIds)) {
            if (qualification.getKind() != QualificationKind.TYPE_RATING || qualification.getAircraftType() == null) {
                continue;
            }
            ratings.computeIfAbsent(qualification.getPerson().getId(), key -> new ArrayList<>())
                    .add(qualification.getAircraftType().getIcaoType());
        }
        ratings.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return ratings;
    }

    private Map<UUID, Long> minutesByPerson(UUID tenantId, OffsetDateTime since) {
        return dutyPeriodRepository.sumBlockMinutesSince(tenantId, since).stream()
                .collect(Collectors.toMap(PersonMinutes::personId, PersonMinutes::minutesOrZero));
    }

    /**
     * Sums the duties already loaded for the detail page instead of asking the
     * database again: the 28-day window is in memory, and the 7-day figure is a
     * subset of it.
     */
    private long countersFor(List<DutyPeriod> duties, OffsetDateTime since, boolean blockOnly) {
        return duties.stream()
                .filter(duty -> !duty.getReportAt().isBefore(since))
                .filter(duty -> blockOnly ? duty.getBlockMinutes() != null : duty.getKind().countsAsDuty())
                .mapToLong(duty -> blockOnly ? duty.getBlockMinutes() : duty.dutyMinutes())
                .sum();
    }

    private CrewRole parseRole(String role) {
        return (role == null || role.isBlank())
                ? null
                : parseEnum(CrewRole.class, role, "CREW_ROLE_UNKNOWN");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String rule) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(rule, "Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
