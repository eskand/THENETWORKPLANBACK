package com.thenetworkplan.networkplan.erp.service.impl;

import com.thenetworkplan.networkplan.admin.repository.SettingRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.erp.domain.ErpActivation;
import com.thenetworkplan.networkplan.erp.domain.ErpActivationCheck;
import com.thenetworkplan.networkplan.erp.domain.ErpActivationNotification;
import com.thenetworkplan.networkplan.erp.domain.ErpChecklistItem;
import com.thenetworkplan.networkplan.erp.domain.ErpLevel;
import com.thenetworkplan.networkplan.erp.domain.ErpLogEntry;
import com.thenetworkplan.networkplan.erp.domain.ErpNotificationType;
import com.thenetworkplan.networkplan.erp.domain.ErpPlan;
import com.thenetworkplan.networkplan.erp.domain.ErpRole;
import com.thenetworkplan.networkplan.erp.domain.ErpSitrep;
import com.thenetworkplan.networkplan.erp.domain.ErpTemplate;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.AssessCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.CheckCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.LevelCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.LogCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.NotifyCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.SitrepCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.SubjectCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ActiveEventDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.AssessmentDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.CatalogueDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.CellDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ChecklistDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ChecklistItemDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ConsoleDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.DriverDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.EventCategoryDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.EventDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.LevelDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.LogEntryDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.NotificationDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.PriorityDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.QuestionDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.QuestionOptionDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ReadinessDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ReferenceDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.SitrepDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.StandDownCriterionDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.SubjectDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.TemplateDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpActivationDto;
import com.thenetworkplan.networkplan.erp.repository.ErpActivationRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpActivationCheckRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpActivationNotificationRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpLogEntryRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpSitrepRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpExerciseRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpChecklistItemRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpLevelRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpNotificationTypeRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpStandDownCriterionRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpTemplateRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpPlanRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpRoleRepository;
import com.thenetworkplan.networkplan.erp.service.ErpConsoleService;
import com.thenetworkplan.networkplan.erp.service.ErpService;
import com.thenetworkplan.networkplan.refdata.domain.ErpEvent;
import com.thenetworkplan.networkplan.refdata.domain.ErpQuestion;
import com.thenetworkplan.networkplan.refdata.domain.ErpQuestionOption;
import com.thenetworkplan.networkplan.refdata.repository.ErpEventCategoryRepository;
import com.thenetworkplan.networkplan.refdata.repository.ErpEventRepository;
import com.thenetworkplan.networkplan.refdata.repository.ErpQuestionOptionRepository;
import com.thenetworkplan.networkplan.refdata.repository.ErpQuestionRepository;
import com.thenetworkplan.networkplan.safety.domain.GroundStaff;
import com.thenetworkplan.networkplan.safety.repository.OccurrenceRepository;
import com.thenetworkplan.networkplan.safety.repository.GroundStaffRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The crisis console.
 *
 * <p><b>Nothing here is a percentage of the whole plan.</b> Progress is counted
 * against the actions that are in force <em>at the current level</em>. A level 2
 * response that has completed every level 2 action is finished, and a console
 * that showed it as 40% because level 4 actions exist would be lying to the
 * only person who cannot afford to be lied to.
 *
 * <p><b>The assessment only escalates.</b> The questionnaire starts at the
 * event's own base level and each answer can raise it. No answer lowers it —
 * an operator must not be able to talk itself down from a MAYDAY.
 */
@Service
@Transactional(readOnly = true)
public class ErpConsoleServiceImpl implements ErpConsoleService {

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Z]+)\\}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ErpPlanRepository planRepository;
    private final ErpRoleRepository roleRepository;
    private final ErpActivationRepository activationRepository;
    private final ErpExerciseRepository exerciseRepository;
    private final ErpLevelRepository levelRepository;
    private final ErpChecklistItemRepository checklistRepository;
    private final ErpNotificationTypeRepository notificationTypeRepository;
    private final ErpTemplateRepository templateRepository;
    private final ErpStandDownCriterionRepository standDownRepository;
    private final ErpActivationCheckRepository checkRepository;
    private final ErpActivationNotificationRepository notificationRepository;
    private final ErpLogEntryRepository logRepository;
    private final ErpSitrepRepository sitrepRepository;
    private final ErpEventCategoryRepository categoryRepository;
    private final ErpEventRepository eventRepository;
    private final ErpQuestionRepository questionRepository;
    private final ErpQuestionOptionRepository optionRepository;
    private final GroundStaffRepository groundStaffRepository;
    private final SettingRepository settingRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final ErpService erpService;

    public ErpConsoleServiceImpl(ErpPlanRepository planRepository,
                                 ErpRoleRepository roleRepository,
                                 ErpActivationRepository activationRepository,
                                 ErpExerciseRepository exerciseRepository,
                                 ErpLevelRepository levelRepository,
                                 ErpChecklistItemRepository checklistRepository,
                                 ErpNotificationTypeRepository notificationTypeRepository,
                                 ErpTemplateRepository templateRepository,
                                 ErpStandDownCriterionRepository standDownRepository,
                                 ErpActivationCheckRepository checkRepository,
                                 ErpActivationNotificationRepository notificationRepository,
                                 ErpLogEntryRepository logRepository,
                                 ErpSitrepRepository sitrepRepository,
                                 ErpEventCategoryRepository categoryRepository,
                                 ErpEventRepository eventRepository,
                                 ErpQuestionRepository questionRepository,
                                 ErpQuestionOptionRepository optionRepository,
                                 GroundStaffRepository groundStaffRepository,
                                 SettingRepository settingRepository,
                                 OccurrenceRepository occurrenceRepository,
                                 ErpService erpService) {
        this.planRepository = planRepository;
        this.roleRepository = roleRepository;
        this.activationRepository = activationRepository;
        this.exerciseRepository = exerciseRepository;
        this.levelRepository = levelRepository;
        this.checklistRepository = checklistRepository;
        this.notificationTypeRepository = notificationTypeRepository;
        this.templateRepository = templateRepository;
        this.standDownRepository = standDownRepository;
        this.checkRepository = checkRepository;
        this.notificationRepository = notificationRepository;
        this.logRepository = logRepository;
        this.sitrepRepository = sitrepRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.groundStaffRepository = groundStaffRepository;
        this.settingRepository = settingRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.erpService = erpService;
    }

    /* ─────────────────────────────────────────────────────── the console ── */

    @Override
    public ConsoleDto findConsole(UUID tenantId) {
        ErpPlan plan = plan(tenantId);
        Map<String, String> settings = settings(tenantId);
        List<LevelDto> levels = levelRepository.findByTenantIdOrderByLevel(tenantId).stream()
                .map(this::toDto).toList();

        List<ErpActivation> activations = activationRepository.findAllForTenant(tenantId);
        ErpActivation active = activations.stream().filter(ErpActivation::isOpen).findFirst().orElse(null);

        short level = active == null ? 0 : active.getLevel();
        List<CellDto> cells = cells(tenantId, plan, active, level);

        ActiveEventDto activeDto = active == null
                ? null
                : activeEvent(tenantId, active, cells, levels);

        List<ErpActivationDto> history = activations.stream()
                .filter(activation -> !activation.isOpen())
                .filter(activation -> activation.getKind().name().equals("REAL"))
                .sorted(Comparator.comparing(ErpActivation::getActivatedAt).reversed())
                .map(erpService::toActivationDto)
                .toList();

        List<LogEntryDto> log = logRepository.findByTenantIdOrderByAtDesc(tenantId).stream()
                .map(this::toDto).toList();

        return new ConsoleDto(
                active == null,
                activeDto,
                plan.getCode(), plan.getTitle(), plan.getRevision(),
                settings.getOrDefault("erp.operatorName", "The Network Plan Airlines"),
                settings.get("safety.aocReference"),
                levels,
                cells,
                readiness(tenantId, cells),
                history,
                exerciseRepository.findByTenantIdOrderByHeldOnDesc(tenantId).stream()
                        .map(erpService::toExerciseDto).toList(),
                log,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * The readiness list of the armed console.
     *
     * <p>Every line is a fact the server can check, not a claim: a cell is ready
     * when the personnel register actually names someone in the post, and the
     * crisis log is ready when the log table answers. The prototype showed the
     * same list from the browser and could only report whether its own
     * localStorage was writable.
     */
    private List<ReadinessDto> readiness(UUID tenantId, List<CellDto> cells) {
        List<ReadinessDto> rows = new ArrayList<>();
        cells.forEach(cell -> rows.add(new ReadinessDto(
                cell.name(),
                cell.leadName() == null ? "unassigned" : cell.leadName(),
                cell.leadName() != null)));

        long occurrences = occurrenceRepository.countByTenantId(tenantId);
        rows.add(new ReadinessDto("SMS occurrence register",
                occurrences + " occurrence(s) on file", occurrences >= 0));

        long staff = groundStaffRepository.findByTenantIdOrderBySortOrder(tenantId).size();
        rows.add(new ReadinessDto("Personnel register",
                staff + " ground staff on file", staff > 0));

        long entries = logRepository.findByTenantIdOrderByAtDesc(tenantId).size();
        rows.add(new ReadinessDto("Crisis log storage",
                entries + " entries stored", true));
        return rows;
    }

    private List<CellDto> cells(UUID tenantId, ErpPlan plan, ErpActivation active, short level) {
        Map<String, String> leads = leadsByRole(tenantId);
        Map<String, Integer> done = new HashMap<>();
        if (active != null) {
            checkRepository.findByActivationId(active.getId())
                    .forEach(check -> done.merge(deptOf(check.getItemCode()), 1, Integer::sum));
        }
        Map<String, List<ErpChecklistItem>> byDept = itemsByDept(tenantId, (short) 1, level);

        return roleRepository.findByPlan(tenantId, plan.getId()).stream()
                .sorted(Comparator.comparingInt(ErpRole::getCallOrder))
                .map(role -> {
                    List<ErpChecklistItem> items = byDept.getOrDefault(role.getRoleCode(), List.of());
                    int total = items.size();
                    int complete = active == null ? 0 : (int) items.stream()
                            .filter(item -> checkRepository
                                    .findByActivationIdAndItemCode(active.getId(), item.getCode())
                                    .isPresent())
                            .count();
                    return new CellDto(
                            role.getRoleCode(), role.getRoleTitle(), role.getScope(), role.getColour(),
                            role.getLeadRole(), leads.get(role.getLeadRole()), role.getCallOrder(),
                            total, complete, total == 0 ? 0 : complete * 100 / total);
                })
                .toList();
    }

    private String deptOf(String itemCode) {
        int dash = itemCode.indexOf('-');
        return dash < 0 ? itemCode : itemCode.substring(0, dash);
    }

    /** The post holders, by job title. Two people in one post both count. */
    private Map<String, String> leadsByRole(UUID tenantId) {
        Map<String, String> out = new HashMap<>();
        groundStaffRepository.findByTenantIdOrderBySortOrder(tenantId).stream()
                .filter(GroundStaff::isActive)
                .forEach(person -> out.merge(person.getRoleTitle(), person.getFullName(),
                        (first, second) -> first + " / " + second));
        return out;
    }

    private Map<String, List<ErpChecklistItem>> itemsByDept(UUID tenantId, short phase, short level) {
        Map<String, List<ErpChecklistItem>> out = new LinkedHashMap<>();
        checklistRepository.findByTenantIdOrderByPhaseAscDeptCodeAscSortOrderAsc(tenantId).stream()
                .filter(item -> item.getPhase() == phase)
                .filter(item -> item.appliesAt(level))
                .forEach(item -> out.computeIfAbsent(item.getDeptCode(), key -> new ArrayList<>()).add(item));
        return out;
    }

    private ActiveEventDto activeEvent(UUID tenantId, ErpActivation active,
                                       List<CellDto> cells, List<LevelDto> levels) {
        short level = active.getLevel();
        LevelDto definition = levels.stream()
                .filter(candidate -> candidate.level() == level)
                .findFirst().orElse(null);

        ChecklistDto phaseZero = phaseZero(tenantId, active);
        List<NotificationDto> notifications = notifications(tenantId, active);

        int outstanding = (phaseZero.total() - phaseZero.done())
                + cells.stream().mapToInt(cell -> cell.itemsTotal() - cell.itemsDone()).sum();

        return new ActiveEventDto(
                active.getId(), active.getReference(), active.getKind().name(),
                level, definition, active.getEventLabel(), active.getEventCode(),
                active.getActivatedAt(),
                Duration.between(active.getActivatedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toMinutes(),
                active.getInitiatedByName(), active.getInitiatedByRole(),
                active.getConcurredByName(), active.getConcurredByRole(),
                active.getOverrideReason(),
                subject(active), active.isCommsIssued(),
                cells, phaseZero,
                priorities(active, phaseZero, notifications, cells),
                notifications,
                sitrepRepository.findByActivationIdOrderByAtDesc(active.getId()).stream()
                        .map(this::toDto).toList(),
                outstanding);
    }

    private ChecklistDto phaseZero(UUID tenantId, ErpActivation active) {
        List<ErpChecklistItem> items = checklistRepository
                .findByTenantIdOrderByPhaseAscDeptCodeAscSortOrderAsc(tenantId).stream()
                .filter(ErpChecklistItem::isPhaseZero)
                .sorted(Comparator.comparingInt(ErpChecklistItem::getSortOrder))
                .toList();
        return checklist(active, "P0", "Phase 0 — first response", "#C8202F", null, items);
    }

    private ChecklistDto checklist(ErpActivation active, String code, String name, String colour,
                                   String leadName, List<ErpChecklistItem> items) {
        List<ChecklistItemDto> dtos = items.stream().map(item -> {
            Optional<ErpActivationCheck> check = active == null
                    ? Optional.empty()
                    : checkRepository.findByActivationIdAndItemCode(active.getId(), item.getCode());
            return new ChecklistItemDto(item.getCode(), item.getDeptCode(), item.getMinLevel(),
                    item.getText(), check.isPresent(),
                    check.map(ErpActivationCheck::getDoneAt).orElse(null),
                    check.map(ErpActivationCheck::getDoneBy).orElse(null));
        }).toList();
        int done = (int) dtos.stream().filter(ChecklistItemDto::done).count();
        return new ChecklistDto(code, name, colour, leadName, dtos, dtos.size(), done,
                dtos.isEmpty() ? 0 : done * 100 / dtos.size());
    }

    private List<NotificationDto> notifications(UUID tenantId, ErpActivation active) {
        short level = active == null ? 0 : active.getLevel();
        return notificationTypeRepository.findByTenantIdOrderBySortOrder(tenantId).stream()
                .map(type -> {
                    Optional<ErpActivationNotification> made = active == null
                            ? Optional.empty()
                            : notificationRepository
                                    .findByActivationIdAndTypeCode(active.getId(), type.getCode());
                    return new NotificationDto(
                            type.getCode(), type.getMinLevel(), type.getTarget(),
                            type.getWithinLabel(), type.getBasis(), type.getNote(),
                            level >= type.getMinLevel(),
                            made.isPresent(),
                            made.map(ErpActivationNotification::getMadeAt).orElse(null),
                            made.map(ErpActivationNotification::getMadeBy).orElse(null),
                            made.map(ErpActivationNotification::getChannel).orElse(null),
                            made.map(ErpActivationNotification::getReference).orElse(null));
                })
                .toList();
    }

    /**
     * What to do next.
     *
     * <p>Ordered by what an inquiry would ask about first: the universal first
     * response, then the notifications whose clock is already running, then the
     * things the level itself demands.
     */
    private List<PriorityDto> priorities(ErpActivation active, ChecklistDto phaseZero,
                                         List<NotificationDto> notifications, List<CellDto> cells) {
        List<PriorityDto> out = new ArrayList<>();
        if (phaseZero.percent() < 100) {
            out.add(new PriorityDto("#e63946",
                    "Complete Phase 0 — " + (phaseZero.total() - phaseZero.done()) + " item(s) outstanding",
                    "checklists"));
        }
        notifications.stream()
                .filter(NotificationDto::required)
                .filter(notification -> !notification.made())
                .filter(notification -> notification.withinLabel().toLowerCase().startsWith("immediate"))
                .forEach(notification -> out.add(new PriorityDto("#e63946",
                        "Notify immediately: " + notification.target(), "notifications")));

        if (active.getLevel() >= 3 && !active.isCommsIssued()) {
            out.add(new PriorityDto("#f08a3c", "Issue the first holding statement", "comms"));
        }
        if (active.getLevel() >= 4) {
            out.add(new PriorityDto("#f08a3c",
                    "Stand up the Family Assistance Centre and the Special Assistance Team", "checklists"));
        }
        if (isBlank(active.getPob())) {
            out.add(new PriorityDto("#f5c842",
                    "Confirm persons on board from the load sheet", "command"));
        }
        cells.stream()
                .filter(cell -> cell.itemsTotal() > 0 && cell.itemsDone() == 0)
                .forEach(cell -> out.add(new PriorityDto("#8a99b3",
                        cell.name() + " has not started", "checklists")));

        if (out.isEmpty()) {
            out.add(new PriorityDto("#3fb27f",
                    "All immediate actions complete. Maintain the response and review the "
                            + "stand-down criteria.", "log"));
        }
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    /* ────────────────────────────────────────────────── the catalogue ── */

    @Override
    public CatalogueDto findCatalogue() {
        Map<String, List<EventDto>> byCategory = new LinkedHashMap<>();
        eventRepository.findAllByOrderBySortOrder().forEach(event ->
                byCategory.computeIfAbsent(event.getCategoryCode(), key -> new ArrayList<>())
                        .add(toDto(event)));

        List<EventCategoryDto> categories = categoryRepository.findAllByOrderBySortOrder().stream()
                .map(category -> new EventCategoryDto(category.getCode(), category.getName(),
                        byCategory.getOrDefault(category.getCode(), List.of())))
                .filter(category -> !category.events().isEmpty())
                .toList();

        Map<String, List<QuestionOptionDto>> options = new LinkedHashMap<>();
        optionRepository.findAllByOrderBySortOrder().forEach(option ->
                options.computeIfAbsent(option.getQuestionCode(), key -> new ArrayList<>())
                        .add(new QuestionOptionDto(option.getValue(), option.getLabel(), option.getLevel())));

        List<QuestionDto> questions = questionRepository.findAllByOrderBySortOrder().stream()
                .map(question -> new QuestionDto(question.getCode(), question.getQuestion(),
                        options.getOrDefault(question.getCode(), List.of())))
                .toList();

        return new CatalogueDto(categories, questions);
    }

    @Override
    public AssessmentDto assess(UUID tenantId, AssessCommand command) {
        Map<String, String> answers = command.answers() == null ? Map.of() : command.answers();
        ErpEvent event = command.eventCode() == null
                ? null
                : eventRepository.findById(command.eventCode()).orElse(null);

        short base = event == null ? 0 : event.getBaseLevel();
        short level = base;
        List<DriverDto> drivers = new ArrayList<>();
        if (event != null) {
            drivers.add(new DriverDto("Event type", event.getLabel(), base));
        }

        List<ErpQuestion> questions = questionRepository.findAllByOrderBySortOrder();
        Map<String, ErpQuestionOption> chosen = new LinkedHashMap<>();
        optionRepository.findAllByOrderBySortOrder().forEach(option -> {
            if (option.getValue().equals(answers.get(option.getQuestionCode()))) {
                chosen.put(option.getQuestionCode(), option);
            }
        });

        for (ErpQuestion question : questions) {
            ErpQuestionOption option = chosen.get(question.getCode());
            if (option == null) {
                continue;
            }
            if (option.getLevel() > 0) {
                drivers.add(new DriverDto(question.getQuestion(), option.getLabel(), option.getLevel()));
            }
            // Only upwards. The questionnaire is not a way out of a MAYDAY.
            if (option.getLevel() > level) {
                level = option.getLevel();
            }
        }
        drivers.sort(Comparator.comparingInt((DriverDto driver) -> driver.level()).reversed());

        short finalLevel = level;
        LevelDto definition = levelRepository.findByTenantIdOrderByLevel(tenantId).stream()
                .filter(candidate -> candidate.getLevel() == finalLevel)
                .findFirst().map(this::toDto).orElse(null);

        return new AssessmentDto(level, base, definition,
                event == null ? null : toDto(event), drivers,
                chosen.size(), questions.size(), chosen.size() == questions.size(),
                level > base);
    }

    /* ────────────────────────────────────────────────── the checklists ── */

    @Override
    public List<ChecklistDto> findChecklists(UUID tenantId, UUID activationId) {
        ErpActivation active = activation(tenantId, activationId);
        Map<String, String> leads = leadsByRole(tenantId);
        Map<String, List<ErpChecklistItem>> byDept =
                itemsByDept(tenantId, (short) 1, active == null ? 4 : active.getLevel());

        ErpPlan plan = plan(tenantId);
        List<ChecklistDto> out = new ArrayList<>();
        out.add(phaseZero(tenantId, active));
        roleRepository.findByPlan(tenantId, plan.getId()).stream()
                .sorted(Comparator.comparingInt(ErpRole::getCallOrder))
                .forEach(role -> out.add(checklist(active, role.getRoleCode(), role.getRoleTitle(),
                        role.getColour(), leads.get(role.getLeadRole()),
                        byDept.getOrDefault(role.getRoleCode(), List.of()))));
        return out;
    }

    /* ─────────────────────────────────────────────────── the templates ── */

    @Override
    public List<TemplateDto> findTemplates(UUID tenantId, UUID activationId) {
        ErpActivation active = activation(tenantId, activationId);
        Map<String, String> tokens = tokens(tenantId, active);
        short level = active == null ? 0 : active.getLevel();

        return templateRepository.findByTenantIdOrderBySortOrder(tenantId).stream()
                .map(template -> render(template, tokens, level))
                .toList();
    }

    /**
     * Fill a template's tokens, and say which ones could not be filled.
     *
     * <p>An unresolved token is left in the body rather than blanked. A holding
     * statement that reads "registered ___" tells the person about to send it
     * that a fact is missing; one that reads "registered " does not.
     */
    private TemplateDto render(ErpTemplate template, Map<String, String> tokens, short level) {
        List<String> unresolved = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(template.getBody());
        StringBuilder body = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = tokens.get(key);
            if (value == null || value.isBlank()) {
                unresolved.add(key);
                value = matcher.group(0);
            }
            matcher.appendReplacement(body, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(body);

        return new TemplateDto(template.getCode(), template.getMinLevel(), template.getAudience(),
                template.getTitle(), body.toString(), level >= template.getMinLevel(),
                unresolved.stream().distinct().toList());
    }

    private Map<String, String> tokens(UUID tenantId, ErpActivation active) {
        Map<String, String> settings = settings(tenantId);
        Map<String, String> out = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        out.put("DATE", DATE.format(now));
        out.put("TIME", TIME.format(now));
        out.put("PHONE", settings.get("erp.crisisPhone"));
        out.put("MEDIA", settings.get("erp.mediaEmail"));
        out.put("AOC", settings.get("safety.aocReference"));
        out.put("AM", nameOnly(settings.get("safety.accountableManager")));
        out.put("SM", nameOnly(settings.get("safety.safetyManager")));

        Map<String, String> leads = leadsByRole(tenantId);
        out.put("CREWLEAD", leads.get("Post Holder — Crew Training"));

        if (active != null) {
            out.put("LEVEL", String.valueOf(active.getLevel()));
            out.put("FLIGHT", active.getFlight());
            out.put("REG", active.getRegistration());
            out.put("TYPE", active.getAircraftType());
            out.put("FROM", active.getOrigin());
            out.put("TO", active.getDestination());
            out.put("POB", active.getPob());
            out.put("DG", active.getDangerousGoods());
            out.put("POS", active.getLastPosition());
            out.put("NATURE", active.getEventLabel());
            out.put("SITUATION", active.getSituation());
            out.put("CLASSIFICATION", active.getLevel() >= 4 ? "ACCIDENT" : "SERIOUS INCIDENT");
            out.put("DATE", DATE.format(active.getActivatedAt()));
            out.put("TIME", TIME.format(active.getActivatedAt()));
        }
        return out;
    }

    /** "Dupont A. — Safety Manager" is stored; a signature wants "Dupont A.". */
    private String nameOnly(String value) {
        if (value == null) {
            return null;
        }
        int dash = value.indexOf('—');
        return dash < 0 ? value.trim() : value.substring(0, dash).trim();
    }

    /* ─────────────────────────────────────────────────── the reference ── */

    @Override
    public ReferenceDto findReference(UUID tenantId) {
        Map<String, String> settings = settings(tenantId);
        ErpPlan plan = plan(tenantId);
        Map<String, String> leads = leadsByRole(tenantId);
        Map<String, List<ErpChecklistItem>> byDept = itemsByDept(tenantId, (short) 1, (short) 4);

        List<CellDto> cells = roleRepository.findByPlan(tenantId, plan.getId()).stream()
                .sorted(Comparator.comparingInt(ErpRole::getCallOrder))
                .map(role -> new CellDto(role.getRoleCode(), role.getRoleTitle(), role.getScope(),
                        role.getColour(), role.getLeadRole(), leads.get(role.getLeadRole()),
                        role.getCallOrder(),
                        byDept.getOrDefault(role.getRoleCode(), List.of()).size(), 0, 0))
                .toList();

        List<ChecklistDto> checklists = new ArrayList<>();
        checklists.add(phaseZero(tenantId, null));
        cells.forEach(cell -> checklists.add(checklist(null, cell.code(), cell.name(), cell.colour(),
                cell.leadName(), byDept.getOrDefault(cell.code(), List.of()))));

        return new ReferenceDto(
                levelRepository.findByTenantIdOrderByLevel(tenantId).stream().map(this::toDto).toList(),
                notifications(tenantId, null),
                templateRepository.findByTenantIdOrderBySortOrder(tenantId).stream()
                        .map(template -> render(template, tokens(tenantId, null), (short) 4)).toList(),
                standDownRepository.findByTenantIdOrderBySortOrder(tenantId).stream()
                        .map(criterion -> new StandDownCriterionDto(criterion.getCode(), criterion.getText()))
                        .toList(),
                cells, checklists,
                settings.get("erp.crisisPhone"),
                settings.get("erp.mediaEmail"),
                settings.get("erp.ercLocation"));
    }

    /* ─────────────────────────────────────────────────────── commands ── */

    @Override
    @Transactional
    public ConsoleDto check(UUID tenantId, UUID activationId, CheckCommand command) {
        ErpActivation active = requireActivation(tenantId, activationId);
        ErpChecklistItem item = checklistRepository
                .findByTenantIdOrderByPhaseAscDeptCodeAscSortOrderAsc(tenantId).stream()
                .filter(candidate -> candidate.getCode().equals(command.itemCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No action of the plan has the code " + command.itemCode()));

        Optional<ErpActivationCheck> existing =
                checkRepository.findByActivationIdAndItemCode(active.getId(), item.getCode());

        if (command.done()) {
            if (existing.isEmpty()) {
                ErpActivationCheck check = new ErpActivationCheck();
                check.setTenantId(tenantId);
                check.setActivationId(active.getId());
                check.setItemCode(item.getCode());
                check.setDoneBy(command.actor().trim());
                check.setDoneAt(OffsetDateTime.now(ZoneOffset.UTC));
                checkRepository.save(check);
                writeLog(tenantId, active, "check", item.getText(), command.actor());
            }
        } else {
            existing.ifPresent(check -> {
                checkRepository.delete(check);
                // An un-tick is an event in its own right: somebody decided the
                // action had not in fact been done, and that belongs on the record.
                writeLog(tenantId, active, "uncheck", item.getText(), command.actor());
            });
        }
        return findConsole(tenantId);
    }

    @Override
    @Transactional
    public ConsoleDto notify(UUID tenantId, UUID activationId, NotifyCommand command) {
        ErpActivation active = requireActivation(tenantId, activationId);
        ErpNotificationType type = notificationTypeRepository.findByTenantIdOrderBySortOrder(tenantId)
                .stream()
                .filter(candidate -> candidate.getCode().equals(command.typeCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No notification of the plan has the code " + command.typeCode()));

        if (notificationRepository.findByActivationIdAndTypeCode(active.getId(), type.getCode())
                .isPresent()) {
            throw new BusinessRuleException("ERP_ALREADY_NOTIFIED",
                    "This notification is already recorded as made");
        }

        ErpActivationNotification made = new ErpActivationNotification();
        made.setTenantId(tenantId);
        made.setActivationId(active.getId());
        made.setTypeCode(type.getCode());
        made.setMadeBy(command.actor().trim());
        made.setMadeAt(OffsetDateTime.now(ZoneOffset.UTC));
        made.setChannel(trim(command.channel()));
        made.setReference(trim(command.reference()));
        notificationRepository.save(made);

        writeLog(tenantId, active, "notified",
                type.getTarget() + " notified" + (made.getChannel() == null
                        ? "" : " (" + made.getChannel() + ")"),
                command.actor());
        return findConsole(tenantId);
    }

    @Override
    @Transactional
    public ConsoleDto changeLevel(UUID tenantId, UUID activationId, LevelCommand command) {
        ErpActivation active = requireActivation(tenantId, activationId);
        short from = active.getLevel();
        short to = command.level();
        if (from == to) {
            throw new BusinessRuleException("ERP_LEVEL_UNCHANGED",
                    "The response is already at level " + to);
        }
        active.setLevel(to);
        activationRepository.save(active);

        writeLog(tenantId, active, to > from ? "escalated" : "deescalated",
                "Level " + from + " to level " + to + " — " + command.reason().trim(),
                command.actor(), to);
        return findConsole(tenantId);
    }

    @Override
    @Transactional
    public ConsoleDto addSitrep(UUID tenantId, UUID activationId, SitrepCommand command) {
        ErpActivation active = requireActivation(tenantId, activationId);
        ErpSitrep sitrep = new ErpSitrep();
        sitrep.setTenantId(tenantId);
        sitrep.setActivationId(active.getId());
        sitrep.setAt(OffsetDateTime.now(ZoneOffset.UTC));
        // The level as it is now: a later escalation must not rewrite what was
        // known when this was written.
        sitrep.setLevel(active.getLevel());
        sitrep.setBody(command.body().trim());
        sitrep.setAuthor(command.author().trim());
        sitrepRepository.save(sitrep);

        writeLog(tenantId, active, "sitrep", "Situation report issued", command.author());
        return findConsole(tenantId);
    }

    @Override
    @Transactional
    public ConsoleDto updateSubject(UUID tenantId, UUID activationId, SubjectCommand command) {
        ErpActivation active = requireActivation(tenantId, activationId);
        List<String> changed = new ArrayList<>();

        changed.addAll(apply("Flight", active.getFlight(), command.flight(), active::setFlight));
        changed.addAll(apply("Registration", active.getRegistration(), command.registration(),
                active::setRegistration));
        changed.addAll(apply("Type", active.getAircraftType(), command.aircraftType(),
                active::setAircraftType));
        changed.addAll(apply("From", active.getOrigin(), command.origin(), active::setOrigin));
        changed.addAll(apply("To", active.getDestination(), command.destination(),
                active::setDestination));
        changed.addAll(apply("Persons on board", active.getPob(), command.pob(), active::setPob));
        changed.addAll(apply("Dangerous goods", active.getDangerousGoods(), command.dangerousGoods(),
                active::setDangerousGoods));
        changed.addAll(apply("Position", active.getLastPosition(), command.lastPosition(),
                active::setLastPosition));
        changed.addAll(apply("Squawk", active.getSquawk(), command.squawk(), active::setSquawk));
        changed.addAll(apply("Fuel", active.getFuelState(), command.fuelState(), active::setFuelState));
        changed.addAll(apply("Souls", active.getSouls(), command.souls(), active::setSouls));

        if (!changed.isEmpty()) {
            activationRepository.save(active);
            writeLog(tenantId, active, "subject", String.join(", ", changed), command.actor());
        }
        return findConsole(tenantId);
    }

    /**
     * Record a field change, and say what changed — not merely that something did.
     *
     * <p>A null field means <em>not supplied</em> and leaves the stored value
     * alone; only an explicit empty string clears one. The aircraft's details
     * arrive piecemeal over the course of an event, and a patch carrying three
     * new facts must not erase the eight it happens not to mention.
     */
    private List<String> apply(String label, String current, String next,
                               java.util.function.Consumer<String> setter) {
        if (next == null) {
            return List.of();
        }
        String value = trim(next);
        if (java.util.Objects.equals(current, value)) {
            return List.of();
        }
        setter.accept(value);
        return List.of(label + ": " + (current == null ? "—" : current) + " to "
                + (value == null ? "—" : value));
    }

    @Override
    @Transactional
    public ConsoleDto log(UUID tenantId, UUID activationId, LogCommand command) {
        ErpActivation active = activationId == null ? null : requireActivation(tenantId, activationId);
        writeLog(tenantId, active, "note", command.text().trim(), command.actor());
        return findConsole(tenantId);
    }

    private void writeLog(UUID tenantId, ErpActivation active, String kind, String text, String actor) {
        writeLog(tenantId, active, kind, text, actor, active == null ? null : active.getLevel());
    }

    private void writeLog(UUID tenantId, ErpActivation active, String kind, String text,
                          String actor, Short level) {
        ErpLogEntry entry = new ErpLogEntry();
        entry.setTenantId(tenantId);
        entry.setActivationId(active == null ? null : active.getId());
        entry.setAt(OffsetDateTime.now(ZoneOffset.UTC));
        entry.setKind(kind);
        entry.setText(text);
        entry.setActor(trim(actor));
        entry.setLevel(level);
        logRepository.save(entry);
    }

    /* ────────────────────────────────────────────────────────── plumbing ── */

    private ErpPlan plan(UUID tenantId) {
        return planRepository.findByTenantIdOrderByApprovedOnDesc(tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No emergency response plan is recorded for this operator"));
    }

    private ErpActivation activation(UUID tenantId, UUID activationId) {
        if (activationId == null) {
            return activationRepository.findAllForTenant(tenantId).stream()
                    .filter(ErpActivation::isOpen).findFirst().orElse(null);
        }
        return activationRepository.findByTenantIdAndId(tenantId, activationId).orElse(null);
    }

    private ErpActivation requireActivation(UUID tenantId, UUID activationId) {
        ErpActivation active = activation(tenantId, activationId);
        if (active == null) {
            throw new ResourceNotFoundException("No activation is open");
        }
        if (!active.isOpen()) {
            throw new BusinessRuleException("ERP_STOOD_DOWN",
                    "This activation was stood down on " + active.getStoodDownAt());
        }
        return active;
    }

    private Map<String, String> settings(UUID tenantId) {
        Map<String, String> out = new HashMap<>();
        settingRepository.findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId)
                .forEach(setting -> out.put(setting.getSettingKey(), setting.getSettingValue()));
        return out;
    }

    private SubjectDto subject(ErpActivation active) {
        return new SubjectDto(active.getFlight(), active.getRegistration(), active.getAircraftType(),
                active.getOrigin(), active.getDestination(), active.getPob(),
                active.getDangerousGoods(), active.getLastPosition(), active.getSquawk(),
                active.getFuelState(), active.getSouls());
    }

    private LevelDto toDto(ErpLevel level) {
        return new LevelDto(level.getLevel(), level.getName(), level.getColour(),
                level.getDescription(), level.getActivation(), level.getStandsUp());
    }

    private EventDto toDto(ErpEvent event) {
        return new EventDto(event.getCode(), event.getCategoryCode(), event.getBaseLevel(),
                event.getSquawk(), event.getLabel(), event.getNote());
    }

    private SitrepDto toDto(ErpSitrep sitrep) {
        return new SitrepDto(sitrep.getId(), sitrep.getAt(), sitrep.getLevel(),
                sitrep.getBody(), sitrep.getAuthor());
    }

    private LogEntryDto toDto(ErpLogEntry entry) {
        return new LogEntryDto(entry.getId(), entry.getAt(), entry.getKind(), entry.getText(),
                entry.getActor(), entry.getLevel());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
