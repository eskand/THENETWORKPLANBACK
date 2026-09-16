package com.thenetworkplan.networkplan.camo.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.camo.domain.AircraftTask;
import com.thenetworkplan.networkplan.camo.domain.AirworthinessReview;
import com.thenetworkplan.networkplan.camo.domain.ArcVerdict;
import com.thenetworkplan.networkplan.camo.domain.DueStatus;
import com.thenetworkplan.networkplan.camo.dto.AircraftCamoDto;
import com.thenetworkplan.networkplan.camo.dto.AircraftUtilisation;
import com.thenetworkplan.networkplan.camo.dto.ArcDto;
import com.thenetworkplan.networkplan.camo.dto.CompleteTaskCommand;
import com.thenetworkplan.networkplan.camo.dto.DueItemDto;
import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.dto.LifeLimitedPartDto;
import com.thenetworkplan.networkplan.camo.dto.UtilisationRowDto;
import com.thenetworkplan.networkplan.camo.dto.WorkOrderDto;
import com.thenetworkplan.networkplan.camo.mapper.CamoMapper;
import com.thenetworkplan.networkplan.camo.repository.AircraftTaskRepository;
import com.thenetworkplan.networkplan.camo.repository.AirworthinessReviewRepository;
import com.thenetworkplan.networkplan.camo.repository.LifeLimitedPartRepository;
import com.thenetworkplan.networkplan.camo.repository.UtilisationRepository;
import com.thenetworkplan.networkplan.camo.repository.WorkOrderRepository;
import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.camo.service.MaintenanceDueRule;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import com.thenetworkplan.networkplan.camoadmin.dto.ProgrammeTaskDto;
import com.thenetworkplan.networkplan.camoadmin.service.CamoAdminService;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.service.TypeFamily;
import com.thenetworkplan.networkplan.techlog.service.TechLogService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * CAMO.
 *
 * <p>The fleet screen costs a fixed number of statements: the fleet, the open
 * tasks, the utilisation of the last four weeks, and one question each to the
 * three modules that own the rest (MEL items, defects, directives). Nothing is
 * queried per registration.
 */
@Service
@Transactional(readOnly = true)
public class CamoServiceImpl implements CamoService {

    private final AircraftTaskRepository taskRepository;
    private final UtilisationRepository utilisationRepository;
    private final AirworthinessReviewRepository reviewRepository;
    private final LifeLimitedPartRepository partRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftService aircraftService;
    private final CamoAdminService camoAdminService;
    private final TechLogService techLogService;
    private final MaintenanceDueRule dueRule;
    private final CamoMapper mapper;

    public CamoServiceImpl(AircraftTaskRepository taskRepository,
                           UtilisationRepository utilisationRepository,
                           AirworthinessReviewRepository reviewRepository,
                           LifeLimitedPartRepository partRepository,
                           WorkOrderRepository workOrderRepository,
                           AircraftRepository aircraftRepository,
                           AircraftService aircraftService,
                           CamoAdminService camoAdminService,
                           TechLogService techLogService,
                           MaintenanceDueRule dueRule,
                           CamoMapper mapper) {
        this.taskRepository = taskRepository;
        this.utilisationRepository = utilisationRepository;
        this.reviewRepository = reviewRepository;
        this.partRepository = partRepository;
        this.workOrderRepository = workOrderRepository;
        this.aircraftRepository = aircraftRepository;
        this.aircraftService = aircraftService;
        this.camoAdminService = camoAdminService;
        this.techLogService = techLogService;
        this.dueRule = dueRule;
        this.mapper = mapper;
    }

    @Override
    public List<FleetStatusRowDto> findFleetStatus(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        if (fleet.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<AircraftTask>> tasksByAircraft = taskRepository.findOpen(tenantId).stream()
                .collect(Collectors.groupingBy(task -> task.getAircraft().getId()));

        Map<UUID, AircraftUtilisation> utilisation = utilisationRepository
                .summariseSince(tenantId, today.minusDays(28)).stream()
                .collect(Collectors.toMap(AircraftUtilisation::aircraftId, row -> row));

        Map<UUID, List<MelItemDto>> melByAircraft = aircraftService.findOpenMelByAircraft(tenantId);
        Map<UUID, Integer> defects = techLogService.countOutstandingDefects(tenantId);
        Map<UUID, Integer> directives = camoAdminService.countOutstandingDirectives(tenantId);

        // Three more fleet-wide reads, still none per registration.
        Map<UUID, AirworthinessReview> arcs = reviewRepository.findInForce(tenantId).stream()
                .collect(Collectors.toMap(review -> review.getAircraft().getId(), review -> review));

        Map<UUID, List<LifeLimitedPartDto>> partsByAircraft = partRepository.findFitted(tenantId).stream()
                .map(part -> mapper.toDto(part, today))
                .collect(Collectors.groupingBy(LifeLimitedPartDto::aircraftId));

        Map<UUID, Long> openOrders = workOrderRepository.findOpen(tenantId).stream()
                .collect(Collectors.groupingBy(order -> order.getAircraft().getId(), Collectors.counting()));

        List<FleetStatusRowDto> rows = new ArrayList<>(fleet.size());
        for (Aircraft aircraft : fleet) {
            List<AircraftTask> tasks = tasksByAircraft.getOrDefault(aircraft.getId(), List.of());

            DueStatus worst = DueStatus.PLANNED;
            AircraftTask next = null;
            MaintenanceDueRule.Verdict nextVerdict = null;
            int overdue = 0;

            for (AircraftTask task : tasks) {
                MaintenanceDueRule.Verdict verdict = dueRule.evaluate(
                        task, aircraft.getHoursSinceNew(), aircraft.getCyclesSinceNew(), today);
                if (verdict.status().severity() > worst.severity()) {
                    worst = verdict.status();
                }
                if (verdict.status() == DueStatus.OVERDUE) {
                    overdue++;
                }
                if (isCloser(verdict, nextVerdict)) {
                    next = task;
                    nextVerdict = verdict;
                }
            }

            AircraftUtilisation used = utilisation.get(aircraft.getId());

            AirworthinessReview arc = arcs.get(aircraft.getId());
            Long arcDays = arc == null ? null : arc.daysLeft(today);

            List<LifeLimitedPartDto> parts = partsByAircraft.getOrDefault(aircraft.getId(), List.of());
            int criticalParts = (int) parts.stream().filter(part -> "CRITICAL".equals(part.severity())).count();
            Integer worstPart = parts.stream()
                    .mapToInt(LifeLimitedPartDto::percentRemaining)
                    .min()
                    .stream().boxed().findFirst().orElse(null);

            rows.add(new FleetStatusRowDto(
                    aircraft.getId(),
                    aircraft.getRegistration(),
                    aircraft.getAircraftType().getIcaoType(),
                    aircraft.getAircraftType().getModel(),
                    // La meme regle que le roster et le crew scheduling : une
                    // seconde definition de « famille » finirait par diverger.
                    TypeFamily.of(aircraft.getAircraftType()),
                    aircraft.getStatus().name(),
                    aircraft.getStatusReason(),
                    aircraft.getHoursSinceNew(),
                    aircraft.getCyclesSinceNew(),
                    used == null ? 0L : used.blockMinutesOrZero(),
                    used == null ? 0L : used.cyclesOrZero(),
                    used == null || used.flights() == null ? 0L : used.flights(),
                    next == null ? null : next.getCode(),
                    next == null ? null : next.getDueOn(),
                    nextVerdict == null ? null : nextVerdict.remainingDays(),
                    tasks.isEmpty() ? DueStatus.UNKNOWN.name() : worst.name(),
                    tasks.size(),
                    overdue,
                    melByAircraft.getOrDefault(aircraft.getId(), List.of()).size(),
                    defects.getOrDefault(aircraft.getId(), 0),
                    directives.getOrDefault(aircraft.getId(), 0),
                    arc == null ? null : arc.getCertificateNo(),
                    arc == null ? null : arc.getExpiresOn(),
                    arcDays,
                    ArcVerdict.of(arcDays).name(),
                    criticalParts,
                    worstPart,
                    openOrders.getOrDefault(aircraft.getId(), 0L).intValue()));
        }

        rows.sort(Comparator.comparingInt((FleetStatusRowDto row) ->
                        DueStatus.valueOf(row.worstDueStatus()).severity()).reversed()
                .thenComparing(FleetStatusRowDto::registration));
        return rows;
    }

    @Override
    public List<DueItemDto> findDueList(UUID tenantId, int horizonDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate horizon = today.plusDays(horizonDays);

        return taskRepository.findOpen(tenantId).stream()
                .map(task -> {
                    Aircraft aircraft = task.getAircraft();
                    return mapper.toDto(task, dueRule.evaluate(
                            task, aircraft.getHoursSinceNew(), aircraft.getCyclesSinceNew(), today));
                })
                // Overdue always shows, whatever its date; the rest only inside the horizon.
                .filter(item -> "OVERDUE".equals(item.status())
                        || (item.dueOn() != null && !item.dueOn().isAfter(horizon))
                        || "UNKNOWN".equals(item.status()))
                .sorted(Comparator
                        .comparingInt((DueItemDto item) -> DueStatus.valueOf(item.status()).severity())
                        .reversed()
                        .thenComparing(item -> item.dueOn() == null ? LocalDate.MAX : item.dueOn()))
                .toList();
    }

    @Override
    public AircraftCamoDto findAircraft(UUID tenantId, UUID aircraftId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, aircraftId)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", aircraftId));

        List<DueItemDto> tasks = taskRepository.findByAircraft(tenantId, aircraftId).stream()
                .map(task -> mapper.toDto(task, dueRule.evaluate(
                        task, aircraft.getHoursSinceNew(), aircraft.getCyclesSinceNew(), today)))
                .toList();

        List<UtilisationRowDto> recent = utilisationRepository
                .findRecentForAircraft(tenantId, aircraftId).stream()
                .map(mapper::toDto)
                .toList();

        List<DirectiveApplicationDto> directives = camoAdminService.findByAircraft(tenantId, aircraftId);

        // Newest first, so the certificate in force leads — when there is one.
        List<ArcDto> certificates = reviewRepository.findByAircraft(tenantId, aircraftId).stream()
                .map(review -> mapper.toDto(review, today))
                .toList();
        ArcDto inForce = certificates.stream().filter(ArcDto::inForce).findFirst().orElse(null);
        List<ArcDto> history = certificates.stream().filter(arc -> !arc.inForce()).toList();

        // Shortest life first: the file opens on the part that will ground the
        // aircraft, not on the one that happens to sort first alphabetically.
        List<LifeLimitedPartDto> parts = partRepository.findByAircraft(tenantId, aircraftId).stream()
                .map(part -> mapper.toDto(part, today))
                .sorted(Comparator.comparingInt(LifeLimitedPartDto::percentRemaining))
                .toList();

        List<WorkOrderDto> orders = workOrderRepository.findByAircraft(tenantId, aircraftId).stream()
                .map(order -> mapper.toDto(order, today))
                .toList();

        FleetStatusRowDto summary = findFleetStatus(tenantId).stream()
                .filter(row -> row.aircraftId().equals(aircraftId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", aircraftId));

        return new AircraftCamoDto(summary, tasks, recent, directives, inForce, history, parts, orders);
    }

    @Override
    public List<LifeLimitedPartDto> findLifeLimitedParts(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return partRepository.findFitted(tenantId).stream()
                .map(part -> mapper.toDto(part, today))
                // Sorted here and not in SQL: remaining life is derived from
                // three possible limits, and the database holds none of it.
                .sorted(Comparator.comparingInt(LifeLimitedPartDto::percentRemaining)
                        .thenComparing(LifeLimitedPartDto::registration)
                        .thenComparing(LifeLimitedPartDto::name))
                .toList();
    }

    @Override
    public List<WorkOrderDto> findOpenWorkOrders(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return workOrderRepository.findOpen(tenantId).stream()
                .map(order -> mapper.toDto(order, today))
                .toList();
    }

    /**
     * Signing off a task writes the next due from the programme interval.
     *
     * <p>The caller supplies what was done and when; the next limit is computed
     * here, from the interval the programme holds. That is the difference
     * between a maintenance record and a spreadsheet.
     */
    @Override
    @Transactional
    public DueItemDto completeTask(UUID tenantId, UUID taskId, CompleteTaskCommand command) {
        AircraftTask task = taskRepository.findOne(tenantId, taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft task", taskId));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (command.completedOn().isAfter(today)) {
            throw new BusinessRuleException("TASK_DATE_IN_FUTURE",
                    "A task cannot be signed off in the future");
        }

        Aircraft aircraft = task.getAircraft();
        BigDecimal atHours = command.atHours() != null ? command.atHours() : aircraft.getHoursSinceNew();
        Integer atCycles = command.atCycles() != null ? command.atCycles() : aircraft.getCyclesSinceNew();

        task.setLastDoneOn(command.completedOn());
        task.setLastDoneHours(atHours);
        task.setLastDoneCycles(atCycles);
        task.setRemark(command.remark());

        ProgrammeTaskDto programme = task.getProgrammeTaskId() == null
                ? null
                : camoAdminService.findProgrammeTask(tenantId, task.getProgrammeTaskId());

        if (programme == null) {
            // A task with no programme behind it keeps no next limit: it becomes
            // UNKNOWN rather than silently repeating the previous interval.
            task.setDueOn(null);
            task.setDueAtHours(null);
            task.setDueAtCycles(null);
        } else {
            task.setDueOn(programme.intervalMonths() == null
                    ? null
                    : command.completedOn().plusMonths(programme.intervalMonths()));
            task.setDueAtHours(programme.intervalHours() == null || atHours == null
                    ? null
                    : atHours.add(programme.intervalHours()));
            task.setDueAtCycles(programme.intervalCycles() == null || atCycles == null
                    ? null
                    : atCycles + programme.intervalCycles());
        }

        AircraftTask saved = taskRepository.save(task);
        return mapper.toDto(saved, dueRule.evaluate(
                saved, aircraft.getHoursSinceNew(), aircraft.getCyclesSinceNew(), today));
    }

    @Override
    @Transactional
    public int rolloutProgrammeTask(UUID tenantId, UUID programmeTaskId) {
        ProgrammeTaskDto programme = camoAdminService.findProgrammeTask(tenantId, programmeTaskId);

        Map<UUID, AircraftTask> existing = new HashMap<>();
        for (AircraftTask task : taskRepository.findOpen(tenantId)) {
            if (task.getCode().equalsIgnoreCase(programme.code())) {
                existing.put(task.getAircraft().getId(), task);
            }
        }

        List<AircraftTask> created = new ArrayList<>();
        for (Aircraft aircraft : aircraftRepository.findFleet(tenantId)) {
            if (!aircraft.getAircraftType().getIcaoType().equals(programme.icaoType())
                    || existing.containsKey(aircraft.getId())) {
                continue;
            }
            AircraftTask task = new AircraftTask();
            task.setTenantId(tenantId);
            task.setAircraft(aircraft);
            task.setProgrammeTaskId(programmeTaskId);
            task.setCode(programme.code());
            task.setTitle(programme.title());
            // No reading yet: the task exists, its limits are unknown, and the due
            // list will say so until someone signs it off for the first time.
            created.add(task);
        }
        taskRepository.saveAll(created);
        return created.size();
    }

    /** The verdict that bites first, by severity then by days remaining. */
    private boolean isCloser(MaintenanceDueRule.Verdict candidate, MaintenanceDueRule.Verdict current) {
        if (current == null) {
            return true;
        }
        if (candidate.status().severity() != current.status().severity()) {
            return candidate.status().severity() > current.status().severity();
        }
        Long left = candidate.remainingDays();
        Long right = current.remainingDays();
        if (left == null) {
            return false;
        }
        return right == null || left < right;
    }
}
