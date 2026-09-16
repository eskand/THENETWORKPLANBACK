package com.thenetworkplan.networkplan.techlog.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.camo.service.UtilisationRecorder;
import com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.refdata.domain.AtaChapter;
import com.thenetworkplan.networkplan.refdata.repository.AtaChapterRepository;
import com.thenetworkplan.networkplan.mel.dto.MelEntryDto;
import com.thenetworkplan.networkplan.mel.dto.RaiseMelCommand;
import com.thenetworkplan.networkplan.mel.service.MelService;
import com.thenetworkplan.networkplan.techlog.domain.Defect;
import com.thenetworkplan.networkplan.techlog.domain.DefectStatus;
import com.thenetworkplan.networkplan.techlog.domain.TechLogEntry;
import com.thenetworkplan.networkplan.techlog.domain.TechLogStatus;
import com.thenetworkplan.networkplan.techlog.dto.DefectDto;
import com.thenetworkplan.networkplan.techlog.dto.ReportDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.ResolveDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.SaveTechLogEntryCommand;
import com.thenetworkplan.networkplan.techlog.dto.TechLogBoardDto;
import com.thenetworkplan.networkplan.techlog.dto.TechLogEntryDto;
import com.thenetworkplan.networkplan.techlog.mapper.TechLogMapper;
import com.thenetworkplan.networkplan.techlog.repository.DefectRepository;
import com.thenetworkplan.networkplan.techlog.repository.TechLogEntryRepository;
import com.thenetworkplan.networkplan.techlog.service.TechLogService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tech Log.
 *
 * <p>Three rules. A page is written once and signed once. Signing is the only
 * event that advances the counters, and it does so through
 * {@code UtilisationRecorder}. A defect leaves the open state in exactly one of
 * two ways — rectified, or deferred under a named MEL line — and the deferral
 * is created by the MEL module, not written here.
 */
@Service
@Transactional(readOnly = true)
public class TechLogServiceImpl implements TechLogService {

    private static final DateTimeFormatter PAGE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TechLogEntryRepository entryRepository;
    private final DefectRepository defectRepository;
    private final AircraftRepository aircraftRepository;
    private final PersonRepository personRepository;
    private final UtilisationRecorder utilisationRecorder;
    private final MelService melService;
    private final TechLogMapper mapper;
    private final AtaChapterRepository ataChapterRepository;

    public TechLogServiceImpl(TechLogEntryRepository entryRepository,
                              DefectRepository defectRepository,
                              AircraftRepository aircraftRepository,
                              PersonRepository personRepository,
                              UtilisationRecorder utilisationRecorder,
                              MelService melService,
                              TechLogMapper mapper,
                              AtaChapterRepository ataChapterRepository) {
        this.entryRepository = entryRepository;
        this.defectRepository = defectRepository;
        this.aircraftRepository = aircraftRepository;
        this.personRepository = personRepository;
        this.utilisationRecorder = utilisationRecorder;
        this.melService = melService;
        this.mapper = mapper;
        this.ataChapterRepository = ataChapterRepository;
    }

    @Override
    public List<TechLogEntryDto> findPages(UUID tenantId, UUID aircraftId, LocalDate from, LocalDate to) {
        List<TechLogEntry> entries = entryRepository.findPages(tenantId, aircraftId, from, to);
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<DefectDto>> defects = defectsByEntry(tenantId, entries);
        return entries.stream()
                .map(entry -> mapper.toDto(entry, defects.getOrDefault(entry.getId(), List.of())))
                .toList();
    }

    @Override
    public TechLogEntryDto findPage(UUID tenantId, UUID entryId) {
        TechLogEntry entry = require(tenantId, entryId);
        Map<UUID, List<DefectDto>> defects = defectsByEntry(tenantId, List.of(entry));
        return mapper.toDto(entry, defects.getOrDefault(entry.getId(), List.of()));
    }

    @Override
    @Transactional
    public TechLogEntryDto createPage(UUID tenantId, SaveTechLogEntryCommand command) {
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, command.aircraftId())
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", command.aircraftId()));

        if (command.legId() != null
                && entryRepository.findByTenantIdAndLegId(tenantId, command.legId()).isPresent()) {
            throw new BusinessRuleException("TECHLOG_PAGE_EXISTS",
                    "This leg already has a tech log page");
        }

        TechLogEntry entry = new TechLogEntry();
        entry.setTenantId(tenantId);
        entry.setAircraft(aircraft);
        entry.setLegId(command.legId());
        entry.setPageRef(nextPageRef(tenantId, aircraft, command.flownOn()));
        entry.setFlownOn(command.flownOn());
        entry.setDepIcao(command.depIcao());
        entry.setArrIcao(command.arrIcao());
        entry.setBlockMinutes(command.blockMinutes());
        entry.setAirMinutes(command.airMinutes());
        entry.setCycles(command.cycles() == null ? 1 : command.cycles());
        entry.setFuelUpliftLitres(command.fuelUpliftLitres());
        entry.setOilAddedLitres(command.oilAddedLitres());
        entry.setCommander(person(tenantId, command.commanderId()));
        entry.setEngineer(person(tenantId, command.engineerId()));
        entry.setStatus(TechLogStatus.OPEN);
        entry.setRemark(command.remark());
        return mapper.toDto(entryRepository.save(entry), List.of());
    }

    /**
     * Signing writes the maintenance record and advances the counters, in one
     * transaction. The audit's "TSN/CSN never fed" is exactly this event never
     * having existed.
     */
    @Override
    @Transactional
    public TechLogEntryDto sign(UUID tenantId, UUID entryId, UUID engineerId) {
        TechLogEntry entry = require(tenantId, entryId);
        if (entry.getStatus() != TechLogStatus.OPEN) {
            throw new BusinessRuleException("TECHLOG_ALREADY_SIGNED",
                    "Page " + entry.getPageRef() + " was signed on " + entry.getSignedAt());
        }
        if (entry.getBlockMinutes() == null || entry.getBlockMinutes() <= 0) {
            throw new BusinessRuleException("TECHLOG_BLOCK_TIME_MISSING",
                    "A page cannot be signed without a block time");
        }

        entry.setStatus(TechLogStatus.SIGNED);
        entry.setSignedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (engineerId != null) {
            entry.setEngineer(person(tenantId, engineerId));
        }
        TechLogEntry saved = entryRepository.save(entry);

        utilisationRecorder.record(
                tenantId,
                saved.getAircraft(),
                saved.getLegId(),
                saved.getFlownOn(),
                saved.getBlockMinutes(),
                saved.getAirMinutes(),
                saved.getCycles(),
                "Tech log " + saved.getPageRef());

        Map<UUID, List<DefectDto>> defects = defectsByEntry(tenantId, List.of(saved));
        return mapper.toDto(saved, defects.getOrDefault(saved.getId(), List.of()));
    }

    @Override
    public List<DefectDto> findDefects(UUID tenantId, UUID aircraftId, boolean openOnly) {
        return defectRepository.findDefects(tenantId, aircraftId, openOnly).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public DefectDto reportDefect(UUID tenantId, UUID entryId, ReportDefectCommand command) {
        TechLogEntry entry = entryId == null ? null : require(tenantId, entryId);
        UUID aircraftId = entry != null ? entry.getAircraft().getId() : command.aircraftId();
        if (aircraftId == null) {
            throw new BusinessRuleException("DEFECT_AIRCRAFT_REQUIRED",
                    "A defect is reported either on a tech log page or against a registration");
        }
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, aircraftId)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", aircraftId));

        Defect defect = new Defect();
        defect.setTenantId(tenantId);
        defect.setAircraft(aircraft);
        defect.setTechLogEntry(entry);
        defect.setAtaChapter(command.ataChapter());
        defect.setDescription(command.description().trim());
        defect.setReportedAt(OffsetDateTime.now(ZoneOffset.UTC));
        defect.setReportedBy(person(tenantId, command.reportedBy()));
        defect.setStatus(DefectStatus.OPEN);
        return mapper.toDto(defectRepository.save(defect));
    }

    @Override
    @Transactional
    public DefectDto resolveDefect(UUID tenantId, UUID defectId, ResolveDefectCommand command) {
        Defect defect = defectRepository.findByTenantIdAndId(tenantId, defectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Defect", defectId));
        if (defect.getStatus() == DefectStatus.CLOSED) {
            throw new BusinessRuleException("DEFECT_ALREADY_CLOSED",
                    "This defect was cleared on " + defect.getClosedAt());
        }

        boolean rectify = command.correctiveAction() != null && !command.correctiveAction().isBlank();
        boolean defer = command.melLibraryItemId() != null;
        if (rectify == defer) {
            throw new BusinessRuleException("DEFECT_RESOLUTION_AMBIGUOUS",
                    "A defect is either rectified with a corrective action, or deferred under a MEL line — not both, not neither");
        }

        if (rectify) {
            defect.setStatus(DefectStatus.CLOSED);
            defect.setCorrectiveAction(command.correctiveAction().trim());
            defect.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
            defect.setClosedBy(command.actorId());
        } else {
            // The MEL module raises the item: category, interval and placard are
            // its rules, and this module must not restate them.
            MelEntryDto melEntry = melService.raise(tenantId, new RaiseMelCommand(
                    defect.getAircraft().getId(),
                    command.melLibraryItemId(),
                    defect.getId(),
                    command.remark() == null ? defect.getDescription() : command.remark(),
                    command.placardFitted(),
                    command.actorId()));
            defect.setStatus(DefectStatus.DEFERRED);
            defect.setMelItemId(melEntry.id());
        }
        return mapper.toDto(defectRepository.save(defect));
    }

    @Override
    public Map<UUID, Integer> countOutstandingDefects(UUID tenantId) {
        return defectRepository.countOutstandingByAircraft(tenantId).stream()
                .collect(Collectors.toMap(AircraftCount::aircraftId, AircraftCount::intValue));
    }

    /**
     * The board.
     *
     * <p>Nothing here is stored. The repeat count in particular has to be
     * recomputed every time: a defect that came back yesterday was not a repeat
     * when it was first filed, and a stored flag would still say it was not.
     */
    @Override
    public TechLogBoardDto findBoard(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Defect> defects = defectRepository.findDefects(tenantId, null, false);

        Map<String, String> systems = ataChapterRepository.findAllByOrderBySortOrder().stream()
                .collect(Collectors.toMap(AtaChapter::getChapter, AtaChapter::getName));

        /* The engineer who released it. Stored as an id; the panel shows a
           name, because "closed by 8c31-..." is not a signature. */
        Map<UUID, String> closers = personRepository.findAll().stream()
                .filter(person -> tenantId.equals(person.getTenantId()))
                .collect(Collectors.toMap(Person::getId, Person::fullName,
                        (first, second) -> first));

        Map<UUID, MelEntryDto> deferrals = melService.findOpen(tenantId).stream()
                .collect(Collectors.toMap(MelEntryDto::id, entry -> entry, (first, second) -> first));

        List<TechLogBoardDto.DefectRowDto> rows = defects.stream()
                .map(defect -> toRow(defect, systems, deferrals.get(defect.getMelItemId()), closers))
                .toList();

        List<Defect> open = defects.stream()
                .filter(defect -> defect.getStatus() == DefectStatus.OPEN)
                .toList();
        List<Defect> deferred = defects.stream()
                .filter(defect -> defect.getStatus() == DefectStatus.DEFERRED)
                .toList();

        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        int closedRecently = (int) defects.stream()
                .filter(defect -> defect.getClosedAt() != null)
                .filter(defect -> defect.getClosedAt().isAfter(sevenDaysAgo))
                .count();

        List<String> affected = defects.stream()
                .filter(defect -> defect.getStatus() != DefectStatus.CLOSED)
                .map(defect -> defect.getAircraft().getRegistration())
                .distinct()
                .sorted()
                .toList();

        return new TechLogBoardDto(
                open.size(),
                (int) open.stream().map(defect -> defect.getAircraft().getId()).distinct().count(),
                deferred.size(),
                deferred.stream()
                        .map(defect -> defect.getAircraft().getRegistration())
                        .distinct().sorted().toList(),
                closedRecently,
                affected.size(),
                aircraftRepository.findFleet(tenantId).size(),
                countRepeats(defects),
                averageDaysToClose(defects),
                rows,
                now);
    }

    /**
     * The same ATA chapter, on the same aircraft, twice inside thirty days.
     *
     * <p>Counted in pairs rather than in rows: two occurrences of one condition
     * are one repeat, and reporting two would double every figure an engineer
     * is trying to act on.
     */
    private int countRepeats(List<Defect> defects) {
        Map<String, List<OffsetDateTime>> byTailAndChapter = new HashMap<>();
        defects.stream()
                .filter(defect -> defect.getAtaChapter() != null)
                .forEach(defect -> byTailAndChapter
                        .computeIfAbsent(
                                defect.getAircraft().getId() + "|" + defect.getAtaChapter(),
                                key -> new ArrayList<>())
                        .add(defect.getReportedAt()));

        int repeats = 0;
        for (List<OffsetDateTime> dates : byTailAndChapter.values()) {
            if (dates.size() < 2) {
                continue;
            }
            List<OffsetDateTime> sorted = dates.stream().sorted().toList();
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i - 1).plusDays(30).isAfter(sorted.get(i))) {
                    repeats++;
                }
            }
        }
        return repeats;
    }

    /** Null while nothing has been closed — zero would read as "instantly". */
    private Double averageDaysToClose(List<Defect> defects) {
        List<Defect> closed = defects.stream()
                .filter(defect -> defect.getClosedAt() != null)
                .toList();
        if (closed.isEmpty()) {
            return null;
        }
        double hours = closed.stream()
                .mapToLong(defect -> java.time.Duration
                        .between(defect.getReportedAt(), defect.getClosedAt()).toHours())
                .average()
                .orElse(0);
        return Math.round(hours / 24 * 10) / 10.0;
    }

    private TechLogBoardDto.DefectRowDto toRow(Defect defect, Map<String, String> systems,
                                               MelEntryDto deferral, Map<UUID, String> closers) {
        Aircraft aircraft = defect.getAircraft();
        return new TechLogBoardDto.DefectRowDto(
                defect.getId(),
                aircraft.getId(),
                aircraft.getRegistration(),
                aircraft.getAircraftType() == null ? null : aircraft.getAircraftType().getModel(),
                aircraft.getAircraftType() == null ? null : aircraft.getAircraftType().getIcaoType(),
                defect.getAtaChapter(),
                defect.getAtaChapter() == null ? null : systems.get(defect.getAtaChapter()),
                defect.getDescription(),
                defect.getReportedAt(),
                defect.getReportedBy() == null ? null : defect.getReportedBy().fullName(),
                defect.getTechLogEntry() == null ? null : defect.getTechLogEntry().getPageRef(),
                defect.getStatus().name(),
                defect.getMelItemId(),
                deferral == null ? null : deferral.reference(),
                deferral == null ? null : deferral.melCategory(),
                deferral == null ? null : deferral.limitation(),
                deferral == null ? null : deferral.dueAt(),
                deferral == null ? null : deferral.daysRemaining(),
                defect.getCorrectiveAction(),
                defect.getClosedBy() == null ? null : closers.get(defect.getClosedBy()),
                defect.getClosedAt());
    }

    // ----------------------------------------------------------------

    private Map<UUID, List<DefectDto>> defectsByEntry(UUID tenantId, List<TechLogEntry> entries) {
        List<UUID> ids = entries.stream().map(TechLogEntry::getId).toList();
        Map<UUID, List<DefectDto>> byEntry = new HashMap<>();
        for (Defect defect : defectRepository.findByEntryIds(tenantId, ids)) {
            byEntry.computeIfAbsent(defect.getTechLogEntry().getId(), key -> new ArrayList<>())
                    .add(mapper.toDto(defect));
        }
        return byEntry;
    }

    private TechLogEntry require(UUID tenantId, UUID entryId) {
        return entryRepository.findOne(tenantId, entryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tech log page", entryId));
    }

    private Person person(UUID tenantId, UUID personId) {
        return personId == null
                ? null
                : personRepository.findByTenantIdAndId(tenantId, personId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Person", personId));
    }

    /**
     * A page reference the crew can read out: registration, date, and a suffix
     * that only grows if the same tail flies twice that day.
     */
    private String nextPageRef(UUID tenantId, Aircraft aircraft, LocalDate flownOn) {
        String base = "TL-" + aircraft.getRegistration().replace("-", "") + "-" + PAGE_DAY.format(flownOn);
        if (!entryRepository.existsByTenantIdAndPageRef(tenantId, base)) {
            return base;
        }
        for (int suffix = 2; suffix < 20; suffix++) {
            String candidate = base + "-" + suffix;
            if (!entryRepository.existsByTenantIdAndPageRef(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("TECHLOG_PAGE_REF_EXHAUSTED",
                "More than twenty pages for " + aircraft.getRegistration() + " on " + flownOn);
    }
}
