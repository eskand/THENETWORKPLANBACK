package com.thenetworkplan.networkplan.crewscheduling.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.crew.service.TypeFamily;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.CrewProperties;
import com.thenetworkplan.networkplan.crew.domain.CrewAssignment;
import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.CrewSeat;
import com.thenetworkplan.networkplan.crew.domain.DutyKind;
import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.domain.FtlVerdict;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.domain.Qualification;
import com.thenetworkplan.networkplan.crew.domain.QualificationKind;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.dto.PersonInstant;
import com.thenetworkplan.networkplan.crew.dto.PersonMinutes;
import com.thenetworkplan.networkplan.crew.mapper.CrewMapper;
import com.thenetworkplan.networkplan.crew.repository.AbsenceRepository;
import com.thenetworkplan.networkplan.crew.repository.CrewAssignmentRepository;
import com.thenetworkplan.networkplan.crew.repository.DutyPeriodRepository;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.crew.repository.QualificationRepository;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.crew.service.CrewDocumentChecker;
import com.thenetworkplan.networkplan.crewscheduling.dto.AssignSeatCommand;
import com.thenetworkplan.networkplan.crewscheduling.dto.CrewAvailability;
import com.thenetworkplan.networkplan.crewscheduling.dto.CrewCandidateDto;
import com.thenetworkplan.networkplan.crewscheduling.dto.SchedulingBoardDto;
import com.thenetworkplan.networkplan.crewscheduling.dto.SchedulingLegDto;
import com.thenetworkplan.networkplan.crewscheduling.service.CrewAvailabilityChecker;
import com.thenetworkplan.networkplan.crewscheduling.service.CrewSchedulingService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.thenetworkplan.networkplan.config.cache.CacheNames;

/**
 * Crew Scheduling.
 *
 * <p>A read model with no table of its own, like the dispatch board: it asks
 * DOM1 for the day's legs and DOM4 for people, qualifications, absences and
 * duty, then composes. Eight statements answer the whole screen whatever the
 * size of the crew or of the programme, and none of them sits inside a loop.
 */
@Service
@Transactional(readOnly = true)
public class CrewSchedulingServiceImpl implements CrewSchedulingService {

    private final LegRepository legRepository;
    private final CrewAssignmentService crewAssignmentService;
    private final CrewAssignmentRepository assignmentRepository;
    private final PersonRepository personRepository;
    private final QualificationRepository qualificationRepository;
    private final AbsenceRepository absenceRepository;
    private final DutyPeriodRepository dutyPeriodRepository;
    private final CrewAvailabilityChecker availabilityChecker;
    private final CrewDocumentChecker documentChecker;
    private final CrewProperties crewProperties;
    private final CrewMapper mapper;

    public CrewSchedulingServiceImpl(LegRepository legRepository,
                                     CrewAssignmentService crewAssignmentService,
                                     CrewAssignmentRepository assignmentRepository,
                                     PersonRepository personRepository,
                                     QualificationRepository qualificationRepository,
                                     AbsenceRepository absenceRepository,
                                     DutyPeriodRepository dutyPeriodRepository,
                                     CrewAvailabilityChecker availabilityChecker,
                                     CrewDocumentChecker documentChecker,
                                     CrewProperties crewProperties,
                                     CrewMapper mapper) {
        this.legRepository = legRepository;
        this.crewAssignmentService = crewAssignmentService;
        this.assignmentRepository = assignmentRepository;
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
        this.absenceRepository = absenceRepository;
        this.dutyPeriodRepository = dutyPeriodRepository;
        this.availabilityChecker = availabilityChecker;
        this.documentChecker = documentChecker;
        this.crewProperties = crewProperties;
        this.mapper = mapper;
    }

    @Override
    public SchedulingBoardDto findBoard(UUID tenantId, LocalDate date, String role) {
        OffsetDateTime from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = from.plusDays(1);

        List<Leg> legs = legRepository.findProgramme(tenantId, from, to);
        List<UUID> legIds = legs.stream().map(Leg::getId).toList();
        Map<UUID, LegCrewDto> crewByLeg = legIds.isEmpty()
                ? Map.of()
                : crewAssignmentService.findByLegIds(tenantId, legIds, date);

        List<SchedulingLegDto> legRows = new ArrayList<>(legs.size());
        int seatsToFill = 0;
        int fullyCrewed = 0;
        for (Leg leg : legs) {
            LegCrewDto crew = crewByLeg.get(leg.getId());
            int filled = crew == null ? 0 : crew.seatsFilled();
            int minimum = crew == null ? 2 : crew.minimumSeats();
            boolean complete = crew != null && crew.complete();
            seatsToFill += Math.max(0, minimum - filled);
            if (complete) {
                fullyCrewed++;
            }
            legRows.add(new SchedulingLegDto(
                    leg.getId(),
                    leg.getFlightNo(),
                    leg.getAircraft().getRegistration(),
                    leg.getAircraft().getAircraftType().getIcaoType(),
                    TypeFamily.of(leg.getAircraft().getAircraftType()),
                    leg.getDepIcao(),
                    leg.getArrIcao(),
                    leg.getStd(),
                    leg.getSta(),
                    leg.getStatus().name(),
                    crew == null ? List.of() : crew.members(),
                    filled,
                    minimum,
                    complete,
                    crew == null ? "UNKNOWN" : crew.ftlStatus(),
                    crew == null ? "UNKNOWN" : crew.documentStatus()));
        }

        List<CrewCandidateDto> pool = buildPool(tenantId, date, role, legs, crewByLeg);
        int absent = (int) pool.stream()
                .filter(candidate -> CrewAvailability.ABSENT.name().equals(candidate.availability()))
                .count();
        int available = (int) pool.stream()
                .filter(candidate -> CrewAvailability.AVAILABLE.name().equals(candidate.availability()))
                .count();

        return new SchedulingBoardDto(
                date, legRows, pool, legs.size(), fullyCrewed, seatsToFill,
                available, absent, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public CrewMemberDto assign(UUID tenantId, UUID legId, AssignSeatCommand command, UUID actorId) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
        Person person = personRepository.findByTenantIdAndId(tenantId, command.personId())
                .orElseThrow(() -> ResourceNotFoundException.of("Person", command.personId()));
        CrewSeat seat = parseSeat(command.seat());
        LocalDate flightDate = leg.getStd().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();

        List<CrewAssignment> existing = assignmentRepository.findByLeg(tenantId, legId);
        if (existing.stream().anyMatch(assignment -> assignment.getSeat() == seat)) {
            throw new BusinessRuleException("SEAT_TAKEN",
                    seat + " is already filled on " + leg.getFlightNo());
        }

        Set<String> ratings = validTypeRatings(tenantId, List.of(person.getId()), flightDate)
                .getOrDefault(person.getId(), Set.of());
        boolean absent = !absenceRepository
                .findOverlapping(tenantId, List.of(person.getId()), flightDate, flightDate).isEmpty();
        boolean alreadyAssigned = assignedOnDay(tenantId, flightDate).contains(person.getId());

        OffsetDateTime reportAt = leg.getStd().minus(crewProperties.getReportBeforeStd());
        DutyPeriod lastDuty = dutyPeriodRepository.findLastBefore(tenantId, person.getId(), reportAt);

        CrewAvailabilityChecker.Verdict verdict = availabilityChecker.check(
                person, flightDate, leg.getAircraft().getAircraftType().getIcaoType(), ratings,
                absent, alreadyAssigned, lastDuty == null ? null : lastDuty.getOffDutyAt(), reportAt);

        if (verdict.availability().blocksAssignment()) {
            throw new BusinessRuleException(
                    "CREW_" + verdict.availability().name(), verdict.reason());
        }

        CrewAssignment assignment = new CrewAssignment();
        assignment.setTenantId(tenantId);
        assignment.setLegId(legId);
        assignment.setPerson(person);
        assignment.setSeat(seat);
        assignment.setDutyStart(reportAt);
        assignment.setDutyEnd(leg.getSta().plus(crewProperties.getOffDutyAfterSta()));
        // No FTL engine yet: the verdict is UNKNOWN and says why, rather than
        // defaulting to OK the way the prototype did.
        assignment.setFtlVerdict(FtlVerdict.UNKNOWN);
        assignment.setFtlReason(verdict.availability() == CrewAvailability.REST_SHORT
                ? verdict.reason()
                : "FTL engine not wired yet (sprint S7); rest and cumulative limits not evaluated");
        assignment.getSource().setAuthor(actorId);
        CrewAssignment saved = assignmentRepository.save(assignment);

        // One assignment, one duty period: the counters stay the sum of one table.
        DutyPeriod duty = dutyPeriodRepository
                .findByTenantIdAndLegIdAndPerson_Id(tenantId, legId, person.getId())
                .orElseGet(DutyPeriod::new);
        duty.setTenantId(tenantId);
        duty.setPerson(person);
        duty.setLegId(legId);
        duty.setKind(DutyKind.FLIGHT_DUTY);
        duty.setReportAt(assignment.getDutyStart());
        duty.setOffDutyAt(assignment.getDutyEnd());
        duty.setBlockMinutes((int) java.time.Duration.between(leg.getStd(), leg.getSta()).toMinutes());
        duty.setSectors(1);
        dutyPeriodRepository.save(duty);

        return mapper.toDto(saved, flightDate, documentChecker.check(person, flightDate));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public void unassign(UUID tenantId, UUID assignmentId) {
        CrewAssignment assignment = assignmentRepository.findById(assignmentId)
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Crew assignment", assignmentId));
        if (assignment.getCheckedInAt() != null) {
            throw new BusinessRuleException("CREW_CHECKED_IN",
                    "This crew member has checked in; record a change of crew instead of deleting the seat");
        }
        dutyPeriodRepository
                .findByTenantIdAndLegIdAndPerson_Id(tenantId, assignment.getLegId(), assignment.getPerson().getId())
                .ifPresent(dutyPeriodRepository::delete);
        assignmentRepository.delete(assignment);
    }

    // ----------------------------------------------------------------

    private List<CrewCandidateDto> buildPool(UUID tenantId,
                                             LocalDate date,
                                             String role,
                                             List<Leg> legs,
                                             Map<UUID, LegCrewDto> crewByLeg) {
        List<Person> people = personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId);
        if (role != null && !role.isBlank()) {
            CrewRole wanted = parseRole(role);
            people = people.stream().filter(person -> person.getMainRole() == wanted).toList();
        }
        if (people.isEmpty()) {
            return List.of();
        }

        List<UUID> personIds = people.stream().map(Person::getId).toList();
        Map<UUID, Set<String>> ratings = validTypeRatings(tenantId, personIds, date);
        Set<UUID> absent = absenceRepository.findOverlapping(tenantId, personIds, date, date).stream()
                .map(absence -> absence.getPerson().getId())
                .collect(Collectors.toSet());
        Set<UUID> assigned = crewByLeg.values().stream()
                .flatMap(crew -> crew.members().stream())
                .map(CrewMemberDto::personId)
                .collect(Collectors.toSet());

        // Rest is only a question when there is a seat to fill: the reference is
        // the report time of the earliest leg still short of crew. With the day
        // fully crewed there is nothing to be rested for, and claiming a short
        // rest against an arbitrary leg would be a finding about nothing.
        OffsetDateTime reference = legs.stream()
                .filter(leg -> {
                    LegCrewDto crew = crewByLeg.get(leg.getId());
                    return crew == null || !crew.complete();
                })
                .map(leg -> leg.getStd().minus(crewProperties.getReportBeforeStd()))
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        OffsetDateTime lastDutyHorizon = reference != null
                ? reference
                : date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<UUID, OffsetDateTime> lastOffDuty = dutyPeriodRepository
                .lastOffDutyBefore(tenantId, lastDutyHorizon).stream()
                .collect(Collectors.toMap(PersonInstant::personId, PersonInstant::instant));
        Map<UUID, Long> block28 = dutyPeriodRepository
                .sumBlockMinutesSince(tenantId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(28)).stream()
                .collect(Collectors.toMap(PersonMinutes::personId, PersonMinutes::minutesOrZero));

        // The type the pool is judged against: the fleet flying that day, so a
        // rating that matches nothing on the programme is not called a match.
        Set<String> typesOfTheDay = legs.stream()
                .map(leg -> leg.getAircraft().getAircraftType().getIcaoType())
                .collect(Collectors.toCollection(HashSet::new));

        List<CrewCandidateDto> pool = new ArrayList<>(people.size());
        for (Person person : people) {
            Set<String> held = ratings.getOrDefault(person.getId(), Set.of());
            String judgedType = typesOfTheDay.stream().filter(held::contains).findFirst()
                    .orElse(typesOfTheDay.size() == 1 ? typesOfTheDay.iterator().next() : null);

            CrewAvailabilityChecker.Verdict verdict = availabilityChecker.check(
                    person, date, judgedType, held,
                    absent.contains(person.getId()),
                    assigned.contains(person.getId()),
                    lastOffDuty.get(person.getId()),
                    reference);

            pool.add(new CrewCandidateDto(
                    person.getId(),
                    person.getStaffNo(),
                    person.fullName(),
                    person.getMainRole().name(),
                    person.getBaseIcao(),
                    held.stream().sorted().toList(),
                    verdict.availability().name(),
                    verdict.reason(),
                    documentChecker.check(person, date).name(),
                    lastOffDuty.get(person.getId()),
                    verdict.restMinutes(),
                    block28.getOrDefault(person.getId(), 0L)));
        }

        pool.sort(Comparator
                .comparingInt((CrewCandidateDto candidate) ->
                        CrewAvailability.valueOf(candidate.availability()).severity())
                .thenComparing(CrewCandidateDto::fullName));
        return pool;
    }

    /** Type ratings that are valid on the flight date, per person. */
    private Map<UUID, Set<String>> validTypeRatings(UUID tenantId, List<UUID> personIds, LocalDate date) {
        Map<UUID, Set<String>> ratings = new HashMap<>();
        for (Qualification qualification : qualificationRepository.findByPersonIds(tenantId, personIds)) {
            if (qualification.getKind() != QualificationKind.TYPE_RATING
                    || qualification.getAircraftType() == null) {
                continue;
            }
            if (qualification.getValidTo() != null && qualification.getValidTo().isBefore(date)) {
                continue;
            }
            ratings.computeIfAbsent(qualification.getPerson().getId(), key -> new HashSet<>())
                    .add(qualification.getAircraftType().getIcaoType());
        }
        return ratings;
    }

    /** Person ids already holding a seat on that day. */
    private Set<UUID> assignedOnDay(UUID tenantId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<UUID> legIds = legRepository.findProgramme(tenantId, from, from.plusDays(1)).stream()
                .map(Leg::getId)
                .toList();
        if (legIds.isEmpty()) {
            return Set.of();
        }
        return assignmentRepository.findByLegIds(tenantId, legIds).stream()
                .map(assignment -> assignment.getPerson().getId())
                .collect(Collectors.toSet());
    }

    private CrewSeat parseSeat(String value) {
        try {
            return CrewSeat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("CREW_SEAT_UNKNOWN", "Unknown seat: " + value);
        }
    }

    private CrewRole parseRole(String value) {
        try {
            return CrewRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("CREW_ROLE_UNKNOWN", "Unknown role: " + value);
        }
    }
}
