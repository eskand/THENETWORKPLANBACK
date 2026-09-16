package com.thenetworkplan.networkplan.roster.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.domain.Qualification;
import com.thenetworkplan.networkplan.crew.domain.QualificationKind;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.crew.dto.PersonMinutes;
import com.thenetworkplan.networkplan.crew.repository.DutyPeriodRepository;
import com.thenetworkplan.networkplan.crew.repository.QualificationRepository;
import com.thenetworkplan.networkplan.crew.service.TypeFamily;
import com.thenetworkplan.networkplan.roster.domain.RosterCode;
import com.thenetworkplan.networkplan.roster.domain.RosterEntry;
import com.thenetworkplan.networkplan.roster.domain.RosterStatus;
import com.thenetworkplan.networkplan.roster.domain.RosterVersion;
import com.thenetworkplan.networkplan.roster.dto.CreateRosterVersionCommand;
import com.thenetworkplan.networkplan.roster.dto.RosterCellDto;
import com.thenetworkplan.networkplan.roster.dto.RosterGridDto;
import com.thenetworkplan.networkplan.roster.dto.RosterMonthDto;
import com.thenetworkplan.networkplan.roster.dto.RosterRowDto;
import com.thenetworkplan.networkplan.roster.dto.RosterVersionDto;
import com.thenetworkplan.networkplan.roster.dto.SaveRosterEntryCommand;
import com.thenetworkplan.networkplan.roster.mapper.RosterMapper;
import com.thenetworkplan.networkplan.roster.repository.RosterEntryRepository;
import com.thenetworkplan.networkplan.roster.repository.RosterVersionRepository;
import com.thenetworkplan.networkplan.roster.service.RosterService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roster.
 *
 * <p>Two rules carry the module. A published version is immutable — every write
 * goes through {@link #requireEditable}, so no path can amend what the crew was
 * told. And a cell is laid on the grid from the version's own entries: the grid
 * is not recomputed from duty periods at display time, it is read.
 */
@Service
@Transactional(readOnly = true)
public class RosterServiceImpl implements RosterService {

    private final RosterVersionRepository versionRepository;
    private final RosterEntryRepository entryRepository;
    private final PersonRepository personRepository;
    private final QualificationRepository qualificationRepository;
    private final DutyPeriodRepository dutyPeriodRepository;
    private final RosterMapper mapper;

    public RosterServiceImpl(RosterVersionRepository versionRepository,
                             RosterEntryRepository entryRepository,
                             PersonRepository personRepository,
                             QualificationRepository qualificationRepository,
                             DutyPeriodRepository dutyPeriodRepository,
                             RosterMapper mapper) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
        this.dutyPeriodRepository = dutyPeriodRepository;
        this.mapper = mapper;
    }

    /**
     * The aircraft family each person is rated on, for the whole grid, in one
     * query.
     *
     * <p>The family itself comes from {@link TypeFamily}, shared with crew
     * scheduling so the two screens cannot disagree. It is read, never derived
     * from the rank: a captain is not a Falcon captain because he is a captain.
     *
     * <p>Someone rated on two families keeps the rating that runs longest —
     * the query returns them ordered by {@code validTo}, so the last one seen
     * for a person wins. A rating with no end date is treated as the longest,
     * because that is what an open-ended rating means.
     */
    private Map<UUID, String> typeRatingsOf(UUID tenantId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byPerson = new LinkedHashMap<>();
        Map<UUID, LocalDate> until = new LinkedHashMap<>();
        for (Qualification qualification : qualificationRepository.findByPersonIds(tenantId, personIds)) {
            if (qualification.getKind() != QualificationKind.TYPE_RATING
                    || qualification.getAircraftType() == null) {
                continue;
            }
            String family = TypeFamily.of(qualification.getAircraftType());
            if (family == null) {
                continue;
            }
            UUID personId = qualification.getPerson().getId();
            LocalDate validTo = qualification.getValidTo();
            LocalDate best = until.get(personId);
            boolean longer = !byPerson.containsKey(personId)
                    || validTo == null
                    || (best != null && validTo.isAfter(best));
            if (longer) {
                byPerson.put(personId, family.toUpperCase(Locale.ROOT));
                until.put(personId, validTo);
            }
        }
        return byPerson;
    }

    @Override
    public List<RosterVersionDto> findVersions(UUID tenantId) {
        return versionRepository.findByTenantIdOrderByPeriodStartDesc(tenantId).stream()
                .map(version -> mapper.toDto(version, entryRepository.countByRosterVersionId(version.getId())))
                .toList();
    }

    @Override
    public RosterGridDto findGrid(UUID tenantId, UUID versionId) {
        RosterVersion version = versionId != null
                ? require(tenantId, versionId)
                : versionRepository
                        .findCovering(tenantId, LocalDate.now(ZoneOffset.UTC), RosterStatus.PUBLISHED)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No published roster covers today; open a draft or ask for a version by id"));

        List<LocalDate> days = version.getPeriodStart()
                .datesUntil(version.getPeriodEnd().plusDays(1))
                .toList();

        // One query for every cell of the period, then one pass to lay them out.
        Map<UUID, List<RosterEntry>> byPerson = new LinkedHashMap<>();
        Map<UUID, Person> people = new LinkedHashMap<>();

        // The whole active crew carries the grid, not only the people already
        // written on it. A draft starts with no entry at all: were the rows
        // built from the entries, an empty draft would show an empty grid, and
        // there would be no line to click to write the first cell. The approved
        // prototype lists the full crew for the same reason — a planner reads a
        // roster to find the blanks in it.
        for (Person person : personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId)) {
            people.put(person.getId(), person);
        }

        for (RosterEntry entry : entryRepository.findByVersion(tenantId, version.getId())) {
            // A cell written on someone since deactivated keeps its line: the
            // roster records what was planned, and dropping the row would drop
            // the cell with it.
            people.putIfAbsent(entry.getPerson().getId(), entry.getPerson());
            byPerson.computeIfAbsent(entry.getPerson().getId(), key -> new ArrayList<>()).add(entry);
        }

        return new RosterGridDto(
                mapper.toDto(version, entryRepository.countByRosterVersionId(version.getId())),
                days,
                rowsOf(tenantId, people, byPerson,
                       version.getPeriodStart(), version.getPeriodEnd()));
    }

    /**
     * The month a planner navigates, assembled from the versions covering it.
     *
     * <p>Nothing is stored as a month: the roster is versioned, and a month
     * usually spans several versions — a published fortnight, a draft for the
     * rest. They are read together and laid on one calendar, because that is the
     * unit the work is planned in, and the approved prototype navigates it.
     *
     * <p>Where two versions write the same person on the same day, the published
     * one is kept. A draft is a proposal; on screen it does not get to overwrite
     * what the crew has already been told.
     */
    @Override
    public RosterMonthDto findMonth(UUID tenantId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<LocalDate> days = from.datesUntil(to.plusDays(1)).toList();

        Map<UUID, List<RosterEntry>> byPerson = new LinkedHashMap<>();
        Map<UUID, Person> people = new LinkedHashMap<>();
        for (Person person : personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId)) {
            people.put(person.getId(), person);
        }

        // ONE cell per (person, day). The key used to carry the code as well,
        // which meant a day written FLT by the published version and RES by a
        // draft kept both, and the grid stacked two icons in one square. A
        // crew member does one thing on a day; the grid shows one thing.
        Map<String, RosterEntry> kept = new LinkedHashMap<>();
        for (RosterEntry entry : entryRepository.findByWindow(tenantId, from, to)) {
            people.putIfAbsent(entry.getPerson().getId(), entry.getPerson());
            String key = entry.getPerson().getId() + "|" + entry.getDutyDate();
            RosterEntry known = kept.get(key);
            if (known == null || outranks(entry, known)) {
                kept.put(key, entry);
            }
        }
        for (RosterEntry entry : kept.values()) {
            byPerson.computeIfAbsent(entry.getPerson().getId(), key -> new ArrayList<>()).add(entry);
        }

        List<RosterVersionDto> versions = versionRepository
                .findByTenantIdOrderByPeriodStartDesc(tenantId).stream()
                .filter(version -> !version.getPeriodStart().isAfter(to)
                        && !version.getPeriodEnd().isBefore(from))
                .map(version -> mapper.toDto(version, entryRepository.countByRosterVersionId(version.getId())))
                .toList();

        return new RosterMonthDto(
                month.toString(),
                days,
                rowsOf(tenantId, people, byPerson, from, to),
                versions);
    }

    private boolean isDraft(RosterEntry entry) {
        return entry.getRosterVersion().getStatus() != RosterStatus.PUBLISHED;
    }

    /**
     * Which of two cells written on the same day is the one to show.
     *
     * <p>Published first: what the crew was told outranks what someone is still
     * drafting. Between two cells of the same standing, the one that puts the
     * person to work wins — a day that is both a flight and a day off is a
     * flight, and showing OFF there would hide a duty.
     */
    private boolean outranks(RosterEntry candidate, RosterEntry known) {
        if (isDraft(known) != isDraft(candidate)) {
            return isDraft(known);
        }
        return weight(candidate.getCode()) > weight(known.getCode());
    }

    /** The operational weight of a code, heaviest first. */
    private int weight(RosterCode code) {
        return switch (code) {
            case FLT -> 10;
            case DH -> 9;
            case POS -> 8;
            case SIM -> 7;
            case TRG -> 6;
            case SBY -> 5;
            case RES -> 4;
            case OFFICE -> 3;
            case SICK -> 2;
            case LVE -> 1;
            case OFF -> 0;
        };
    }

    /**
     * One row per person: the cells laid out, the days counted, the hours summed.
     *
     * <p>The hours come from {@code crew.duty_periods} and never from the codes.
     * A cell says what a day is for; only a duty period says how long it lasted,
     * and the prototype's "04:00 this month" is a total of flying, not a count of
     * days marked FLT.
     */
    private List<RosterRowDto> rowsOf(UUID tenantId,
                                      Map<UUID, Person> people,
                                      Map<UUID, List<RosterEntry>> byPerson,
                                      LocalDate from,
                                      LocalDate to) {
        Map<UUID, String> typeRatings = typeRatingsOf(tenantId, people.keySet());

        Map<UUID, Long> blockMinutes = new LinkedHashMap<>();
        for (PersonMinutes minutes : dutyPeriodRepository.sumBlockMinutesBetween(
                tenantId,
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC))) {
            blockMinutes.put(minutes.personId(), minutes.minutesOrZero());
        }

        List<RosterRowDto> rows = new ArrayList<>(people.size());
        for (Map.Entry<UUID, Person> personEntry : people.entrySet()) {
            Person person = personEntry.getValue();
            List<RosterEntry> entries = byPerson.getOrDefault(personEntry.getKey(), List.of());
            List<RosterCellDto> cells = entries.stream().map(mapper::toDto).toList();
            int working = (int) entries.stream().filter(e -> e.getCode().isWorking()).count();
            int off = (int) entries.stream()
                    .filter(e -> e.getCode() == RosterCode.OFF || e.getCode() == RosterCode.LVE)
                    .count();
            rows.add(new RosterRowDto(
                    person.getId(), person.getStaffNo(), person.fullName(),
                    person.getMainRole().name(), typeRatings.get(person.getId()),
                    cells, working, off,
                    blockMinutes.getOrDefault(person.getId(), 0L)));
        }
        return rows;
    }

    @Override
    @Transactional
    public RosterVersionDto create(UUID tenantId, CreateRosterVersionCommand command) {
        if (command.periodEnd().isBefore(command.periodStart())) {
            throw new BusinessRuleException("ROSTER_WINDOW_INVALID",
                    "A roster period cannot end before it starts");
        }
        RosterVersion version = new RosterVersion();
        version.setTenantId(tenantId);
        version.setLabel(command.label().trim());
        version.setPeriodStart(command.periodStart());
        version.setPeriodEnd(command.periodEnd());
        version.setStatus(RosterStatus.DRAFT);
        RosterVersion saved = versionRepository.save(version);

        long copied = 0;
        if (command.copyFromVersionId() != null) {
            copied = copyCells(tenantId, require(tenantId, command.copyFromVersionId()), saved);
        }
        return mapper.toDto(saved, copied);
    }

    @Override
    @Transactional
    public RosterVersionDto publish(UUID tenantId, UUID versionId, UUID actorId) {
        RosterVersion version = require(tenantId, versionId);
        if (version.getStatus() != RosterStatus.DRAFT) {
            throw new BusinessRuleException("ROSTER_NOT_DRAFT",
                    "Only a draft can be published; this version is " + version.getStatus());
        }
        long cells = entryRepository.countByRosterVersionId(versionId);
        if (cells == 0) {
            throw new BusinessRuleException("ROSTER_EMPTY",
                    "An empty roster cannot be published");
        }
        version.setStatus(RosterStatus.PUBLISHED);
        version.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        version.setPublishedBy(actorId);
        return mapper.toDto(versionRepository.save(version), cells);
    }

    @Override
    @Transactional
    public RosterCellDto saveEntry(UUID tenantId, UUID versionId, SaveRosterEntryCommand command) {
        RosterVersion version = requireEditable(tenantId, versionId);
        if (!version.covers(command.dutyDate())) {
            throw new BusinessRuleException("ROSTER_DAY_OUTSIDE_PERIOD",
                    command.dutyDate() + " is outside " + version.getPeriodStart() + " → " + version.getPeriodEnd());
        }
        Person person = personRepository.findByTenantIdAndId(tenantId, command.personId())
                .orElseThrow(() -> ResourceNotFoundException.of("Person", command.personId()));
        RosterCode code = parseCode(command.code());

        // One code per (version, person, day, code): re-saving the same code is idempotent.
        RosterEntry entry = entryRepository
                .findCell(tenantId, versionId, command.personId(), command.dutyDate()).stream()
                .filter(existing -> existing.getCode() == code)
                .findFirst()
                .orElseGet(RosterEntry::new);

        entry.setTenantId(tenantId);
        entry.setRosterVersion(version);
        entry.setPerson(person);
        entry.setDutyDate(command.dutyDate());
        entry.setCode(code);
        entry.setRemark(command.remark());
        return mapper.toDto(entryRepository.save(entry));
    }

    @Override
    @Transactional
    public void removeEntry(UUID tenantId, UUID entryId) {
        RosterEntry entry = entryRepository.findByTenantIdAndId(tenantId, entryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Roster entry", entryId));
        if (!entry.getRosterVersion().getStatus().isEditable()) {
            throw new BusinessRuleException("ROSTER_PUBLISHED",
                    "A published roster cannot be changed; open a new version instead");
        }
        entryRepository.delete(entry);
    }

    // ----------------------------------------------------------------

    private long copyCells(UUID tenantId, RosterVersion from, RosterVersion to) {
        long offsetDays = ChronoUnit.DAYS.between(from.getPeriodStart(), to.getPeriodStart());
        List<RosterEntry> copies = new ArrayList<>();
        for (RosterEntry source : entryRepository.findByVersion(tenantId, from.getId())) {
            LocalDate day = source.getDutyDate().plusDays(offsetDays);
            if (!to.covers(day)) {
                continue;
            }
            RosterEntry copy = new RosterEntry();
            copy.setTenantId(tenantId);
            copy.setRosterVersion(to);
            copy.setPerson(source.getPerson());
            copy.setDutyDate(day);
            copy.setCode(source.getCode());
            copy.setRemark("Copied from " + from.getLabel());
            copies.add(copy);
        }
        entryRepository.saveAll(copies);
        return copies.size();
    }

    private RosterVersion require(UUID tenantId, UUID versionId) {
        return versionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Roster version", versionId));
    }

    /** The single gate every write goes through. */
    private RosterVersion requireEditable(UUID tenantId, UUID versionId) {
        RosterVersion version = require(tenantId, versionId);
        if (!version.getStatus().isEditable()) {
            throw new BusinessRuleException("ROSTER_PUBLISHED",
                    "A published roster cannot be changed; open a new version instead");
        }
        return version;
    }

    private RosterCode parseCode(String value) {
        try {
            return RosterCode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("ROSTER_CODE_UNKNOWN", "Unknown roster code: " + value);
        }
    }
}
