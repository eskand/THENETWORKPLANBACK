package com.thenetworkplan.networkplan.training.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import com.thenetworkplan.networkplan.training.domain.EnrolmentStatus;
import com.thenetworkplan.networkplan.training.domain.TrainingCourse;
import com.thenetworkplan.networkplan.training.domain.TrainingEnrolment;
import com.thenetworkplan.networkplan.training.domain.TrainingRecord;
import com.thenetworkplan.networkplan.training.domain.TrainingSession;
import com.thenetworkplan.networkplan.training.dto.ComplianceCellDto;
import com.thenetworkplan.networkplan.training.dto.ComplianceRowDto;
import com.thenetworkplan.networkplan.training.dto.EnrolCommand;
import com.thenetworkplan.networkplan.training.dto.PersonTrainingDto;
import com.thenetworkplan.networkplan.training.dto.RecordCompletionCommand;
import com.thenetworkplan.networkplan.training.dto.SaveSessionCommand;
import com.thenetworkplan.networkplan.training.dto.TrainingComplianceDto;
import com.thenetworkplan.networkplan.training.dto.TrainingCourseDto;
import com.thenetworkplan.networkplan.training.dto.TrainingEnrolmentDto;
import com.thenetworkplan.networkplan.training.dto.TrainingRecordDto;
import com.thenetworkplan.networkplan.training.dto.TrainingSessionDto;
import com.thenetworkplan.networkplan.training.dto.UpdateEnrolmentCommand;
import com.thenetworkplan.networkplan.training.mapper.TrainingMapper;
import com.thenetworkplan.networkplan.training.repository.TrainingCourseRepository;
import com.thenetworkplan.networkplan.training.repository.TrainingEnrolmentRepository;
import com.thenetworkplan.networkplan.training.repository.TrainingRecordRepository;
import com.thenetworkplan.networkplan.training.repository.TrainingSessionRepository;
import com.thenetworkplan.networkplan.training.service.TrainingService;
import com.thenetworkplan.networkplan.training.service.TrainingValidityRule;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Training.
 *
 * <p>The compliance matrix — the screen the audit's "15 files out of 101"
 * finding is about — costs three statements: the mandatory courses, the active
 * crew, and every record. The newest record per (person, course) is kept in one
 * pass, and the missing pairs become {@code MISSING} cells rather than blanks.
 */
@Service
@Transactional(readOnly = true)
public class TrainingServiceImpl implements TrainingService {

    private final TrainingCourseRepository courseRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingEnrolmentRepository enrolmentRepository;
    private final TrainingRecordRepository recordRepository;
    private final PersonRepository personRepository;
    private final CrewDocumentChecker documentChecker;
    private final TrainingValidityRule validityRule;
    private final TrainingMapper mapper;

    public TrainingServiceImpl(TrainingCourseRepository courseRepository,
                               TrainingSessionRepository sessionRepository,
                               TrainingEnrolmentRepository enrolmentRepository,
                               TrainingRecordRepository recordRepository,
                               PersonRepository personRepository,
                               CrewDocumentChecker documentChecker,
                               TrainingValidityRule validityRule,
                               TrainingMapper mapper) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.recordRepository = recordRepository;
        this.personRepository = personRepository;
        this.documentChecker = documentChecker;
        this.validityRule = validityRule;
        this.mapper = mapper;
    }

    @Override
    public List<TrainingCourseDto> findCourses(UUID tenantId) {
        return courseRepository.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<TrainingSessionDto> findSessions(UUID tenantId, LocalDate from, LocalDate to) {
        List<TrainingSession> sessions = sessionRepository.findInWindow(
                tenantId, from.atStartOfDay().atOffset(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TrainingEnrolmentDto>> bySession = enrolmentRepository
                .findBySessionIds(tenantId, sessions.stream().map(TrainingSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        enrolment -> enrolment.getSession().getId(),
                        Collectors.mapping(mapper::toDto, Collectors.toList())));
        return sessions.stream()
                .map(session -> mapper.toDto(session, bySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    @Override
    public TrainingComplianceDto findCompliance(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<TrainingCourse> courses = courseRepository.findByTenantIdAndMandatoryTrueOrderByCodeAsc(tenantId);
        List<Person> people = personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId);

        // Records arrive newest first, so the first one seen for a pair is the one that counts.
        Map<UUID, Map<UUID, TrainingRecord>> latest = new HashMap<>();
        for (TrainingRecord record : recordRepository.findAllForMatrix(tenantId, true)) {
            latest.computeIfAbsent(record.getPerson().getId(), key -> new HashMap<>())
                    .putIfAbsent(record.getCourse().getId(), record);
        }

        int compliant = 0;
        int expiring = 0;
        int expired = 0;
        int missing = 0;
        List<ComplianceRowDto> rows = new ArrayList<>(people.size());

        for (Person person : people) {
            Map<UUID, TrainingRecord> held = latest.getOrDefault(person.getId(), Map.of());
            List<ComplianceCellDto> cells = new ArrayList<>(courses.size());
            String worst = "VALID";
            LocalDate nextExpiry = null;

            for (TrainingCourse course : courses) {
                TrainingRecord record = held.get(course.getId());
                if (record == null) {
                    cells.add(ComplianceCellDto.missing(course.getCode()));
                    missing++;
                    worst = "MISSING";
                    continue;
                }
                DocumentValidity validity = documentChecker.checkDate(record.getValidTo(), today);
                cells.add(new ComplianceCellDto(
                        course.getCode(), validity.name(), record.getCompletedOn(), record.getValidTo()));

                switch (validity) {
                    case VALID -> compliant++;
                    case EXPIRING -> expiring++;
                    case EXPIRED -> expired++;
                    case UNKNOWN -> { /* a record with no expiry: counted nowhere, shown as unknown */ }
                }
                worst = worseOf(worst, validity.name());
                if (record.getValidTo() != null
                        && !record.getValidTo().isBefore(today)
                        && (nextExpiry == null || record.getValidTo().isBefore(nextExpiry))) {
                    nextExpiry = record.getValidTo();
                }
            }

            rows.add(new ComplianceRowDto(
                    person.getId(), person.getStaffNo(), person.fullName(),
                    person.getMainRole().name(), cells, worst, nextExpiry));
        }

        rows.sort(Comparator.comparingInt((ComplianceRowDto row) -> rank(row.worstStatus())).reversed()
                .thenComparing(ComplianceRowDto::fullName));

        return new TrainingComplianceDto(
                courses.stream().map(mapper::toDto).toList(),
                rows,
                people.size(),
                compliant,
                expiring,
                expired,
                missing,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public PersonTrainingDto findPersonFile(UUID tenantId, UUID personId) {
        Person person = personRepository.findByTenantIdAndId(tenantId, personId)
                .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<TrainingRecordDto> records = recordRepository.findByPerson(tenantId, personId).stream()
                .map(record -> mapper.toDto(record, documentChecker.checkDate(record.getValidTo(), today).name()))
                .toList();

        List<TrainingEnrolmentDto> enrolments = enrolmentRepository.findByPerson(tenantId, personId).stream()
                .map(mapper::toDto)
                .toList();

        return new PersonTrainingDto(person.getId(), person.getStaffNo(), person.fullName(), records, enrolments);
    }

    @Override
    @Transactional
    public TrainingSessionDto createSession(UUID tenantId, SaveSessionCommand command) {
        TrainingCourse course = requireCourse(tenantId, command.courseCode());
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new BusinessRuleException("SESSION_WINDOW_INVALID", "A session cannot end before it starts");
        }
        TrainingSession session = new TrainingSession();
        session.setTenantId(tenantId);
        session.setCourse(course);
        session.setStartsAt(command.startsAt());
        session.setEndsAt(command.endsAt());
        session.setLocation(command.location());
        session.setCapacity(command.capacity());
        if (command.instructorId() != null) {
            session.setInstructor(requirePerson(tenantId, command.instructorId()));
        }
        return mapper.toDto(sessionRepository.save(session), List.of());
    }

    @Override
    @Transactional
    public TrainingEnrolmentDto enrol(UUID tenantId, UUID sessionId, EnrolCommand command) {
        TrainingSession session = sessionRepository.findOne(tenantId, sessionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Training session", sessionId));
        if (!session.getStatus().acceptsEnrolment()) {
            throw new BusinessRuleException("SESSION_CLOSED",
                    "Session is " + session.getStatus() + " and no longer accepts enrolments");
        }
        if (enrolmentRepository.countBySessionId(sessionId) >= session.getCapacity()) {
            throw new BusinessRuleException("SESSION_FULL",
                    "Session is full (" + session.getCapacity() + " seats)");
        }
        Person person = requirePerson(tenantId, command.personId());

        TrainingEnrolment enrolment = new TrainingEnrolment();
        enrolment.setTenantId(tenantId);
        enrolment.setSession(session);
        enrolment.setPerson(person);
        enrolment.setStatus(EnrolmentStatus.BOOKED);
        return mapper.toDto(enrolmentRepository.save(enrolment));
    }

    /**
     * Marking attendance and issuing the certificate are one transaction: a
     * record cannot exist without the attendance that produced it.
     */
    @Override
    @Transactional
    public TrainingEnrolmentDto updateEnrolment(UUID tenantId, UUID enrolmentId, UpdateEnrolmentCommand command) {
        TrainingEnrolment enrolment = enrolmentRepository.findOne(tenantId, enrolmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Enrolment", enrolmentId));
        EnrolmentStatus target = parseEnum(EnrolmentStatus.class, command.status(), "ENROLMENT_STATUS_UNKNOWN");

        enrolment.setStatus(target);
        enrolment.setScore(command.score());
        TrainingEnrolment saved = enrolmentRepository.save(enrolment);

        if (target.producesRecord()) {
            TrainingSession session = enrolment.getSession();
            LocalDate completedOn = session.getEndsAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            writeRecord(tenantId, enrolment.getPerson(), session.getCourse(), session,
                    completedOn, command.score(), session.getInstructor(), "Session " + session.getId());
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public TrainingRecordDto recordCompletion(UUID tenantId, RecordCompletionCommand command) {
        Person person = requirePerson(tenantId, command.personId());
        TrainingCourse course = requireCourse(tenantId, command.courseCode());
        Person instructor = command.instructorId() == null ? null : requirePerson(tenantId, command.instructorId());

        TrainingRecord record = writeRecord(tenantId, person, course, null,
                command.completedOn(), command.score(), instructor, command.reference());
        return mapper.toDto(record,
                documentChecker.checkDate(record.getValidTo(), LocalDate.now(ZoneOffset.UTC)).name());
    }

    // ----------------------------------------------------------------
    //  helpers
    // ----------------------------------------------------------------

    /** The single place a training record is created, and the only caller of the validity rule. */
    private TrainingRecord writeRecord(UUID tenantId,
                                       Person person,
                                       TrainingCourse course,
                                       TrainingSession session,
                                       LocalDate completedOn,
                                       java.math.BigDecimal score,
                                       Person instructor,
                                       String reference) {
        if (completedOn.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new BusinessRuleException("TRAINING_DATE_IN_FUTURE",
                    "A course cannot be recorded as completed in the future");
        }
        TrainingRecord record = new TrainingRecord();
        record.setTenantId(tenantId);
        record.setPerson(person);
        record.setCourse(course);
        record.setSession(session);
        record.setCompletedOn(completedOn);
        record.setValidTo(validityRule.validUntil(course, completedOn));
        record.setScore(score);
        record.setInstructor(instructor);
        record.setReference(reference);
        return recordRepository.save(record);
    }

    private TrainingCourse requireCourse(UUID tenantId, String code) {
        return courseRepository.findByTenantIdAndCode(tenantId, code.trim().toUpperCase())
                .orElseThrow(() -> ResourceNotFoundException.of("Training course", code));
    }

    private Person requirePerson(UUID tenantId, UUID personId) {
        return personRepository.findByTenantIdAndId(tenantId, personId)
                .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
    }

    /** Ordering of the five cell states, worst last. */
    private static int rank(String status) {
        return switch (status) {
            case "VALID" -> 0;
            case "UNKNOWN" -> 1;
            case "EXPIRING" -> 2;
            case "EXPIRED" -> 3;
            case "MISSING" -> 4;
            default -> 0;
        };
    }

    private static String worseOf(String left, String right) {
        return rank(right) > rank(left) ? right : left;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String rule) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(rule, "Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
