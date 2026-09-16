package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.safety.domain.ActionStatus;
import com.thenetworkplan.networkplan.safety.domain.Campaign;
import com.thenetworkplan.networkplan.safety.domain.CampaignAcknowledgement;
import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.OccurrenceCategory;
import com.thenetworkplan.networkplan.safety.domain.OccurrenceStatus;
import com.thenetworkplan.networkplan.safety.domain.RiskLevel;
import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import com.thenetworkplan.networkplan.safety.domain.SafetyAction;
import com.thenetworkplan.networkplan.safety.dto.CampaignCount;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.AcknowledgeCampaignCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.AssessRiskCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.CloseOccurrenceCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.CreateActionCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.FileWithAuthorityCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.ReportOccurrenceCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.UpdateActionCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.ActionDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.CampaignDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.OccurrenceDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.PromotionBoardDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.ReportingSummaryDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.RiskCellDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.SafetyBoardDto;
import com.thenetworkplan.networkplan.safety.mapper.SafetyMapper;
import com.thenetworkplan.networkplan.safety.repository.CampaignAcknowledgementRepository;
import com.thenetworkplan.networkplan.safety.repository.CampaignRepository;
import com.thenetworkplan.networkplan.safety.repository.OccurrenceRepository;
import com.thenetworkplan.networkplan.safety.repository.RiskMatrixRepository;
import com.thenetworkplan.networkplan.safety.repository.SafetyActionRepository;
import com.thenetworkplan.networkplan.safety.service.RiskAssessmentRule;
import com.thenetworkplan.networkplan.safety.service.SafetyOverviewService;
import com.thenetworkplan.networkplan.safety.service.SafetyService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety Manager, Safety Reports and Safety Promotion.
 *
 * <p>Four rules answer four audit findings. The risk level is read from the
 * operator matrix and stored with its assessor. An occurrence cannot be closed
 * while an action is open. A report filed anonymously never returns its
 * reporter. And the ECCAIRS filing is a recorded event with the authority's
 * own reference, not a checkbox.
 */
@Service
@Transactional(readOnly = true)
public class SafetyServiceImpl implements SafetyService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OccurrenceRepository occurrenceRepository;
    private final SafetyActionRepository actionRepository;
    private final RiskMatrixRepository matrixRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignAcknowledgementRepository acknowledgementRepository;
    private final PersonRepository personRepository;
    private final AircraftRepository aircraftRepository;
    private final RiskAssessmentRule riskRule;
    private final SafetyMapper mapper;
    private final SafetyOverviewService overviewService;

    /**
     * La politique de Just Culture, telle que l'exploitant la publie.
     *
     * <p>Enoncee une fois et servie a tous les ecrans qui la montrent — le
     * formulaire de signalement, la promotion, la guidance. Trois copies du
     * meme texte finiraient par diverger, et c'est un texte que l'exploitant
     * peut avoir a defendre.
     */
    static final String JUST_CULTURE =
            "Reporting an honest error will not of itself lead to disciplinary action. "
                    + "Wilful violations and destructive acts remain outside the protection of "
                    + "the policy.";

    public SafetyServiceImpl(OccurrenceRepository occurrenceRepository,
                             SafetyActionRepository actionRepository,
                             RiskMatrixRepository matrixRepository,
                             CampaignRepository campaignRepository,
                             CampaignAcknowledgementRepository acknowledgementRepository,
                             PersonRepository personRepository,
                             AircraftRepository aircraftRepository,
                             RiskAssessmentRule riskRule,
                             SafetyMapper mapper,
                             SafetyOverviewService overviewService) {
        this.occurrenceRepository = occurrenceRepository;
        this.actionRepository = actionRepository;
        this.matrixRepository = matrixRepository;
        this.campaignRepository = campaignRepository;
        this.acknowledgementRepository = acknowledgementRepository;
        this.personRepository = personRepository;
        this.aircraftRepository = aircraftRepository;
        this.riskRule = riskRule;
        this.mapper = mapper;
        this.overviewService = overviewService;
    }

    @Override
    public SafetyBoardDto findBoard(UUID tenantId, String status, int windowDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime since = today.minusDays(windowDays).atStartOfDay().atOffset(ZoneOffset.UTC);
        OccurrenceStatus filter = (status == null || status.isBlank())
                ? null
                : parseEnum(OccurrenceStatus.class, status, "OCCURRENCE_STATUS_UNKNOWN");

        List<Occurrence> occurrences = occurrenceRepository.findAll(tenantId, filter, since);
        List<OccurrenceDto> rows = withActions(tenantId, occurrences, today);

        // The matrix carries how many open occurrences sit in each cell: the
        // heat map is a count of rows, not a colour someone chose.
        Map<String, Integer> cellCounts = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            if (occurrence.getRiskSeverity() != null && occurrence.getRiskProbability() != null
                    && occurrence.getStatus() != OccurrenceStatus.CLOSED) {
                cellCounts.merge(occurrence.getRiskSeverity() + occurrence.getRiskProbability(), 1, Integer::sum);
            }
        }
        List<RiskCellDto> matrix = matrixRepository
                .findByTenantIdOrderBySeverityAscProbabilityAsc(tenantId).stream()
                .map(cell -> new RiskCellDto(
                        cell.getSeverity(), cell.getProbability(),
                        cell.getRiskLevel().name(), cell.getActionRequired(),
                        cellCounts.getOrDefault(cell.getSeverity() + cell.getProbability(), 0)))
                .toList();

        int reported = 0;
        int underReview = 0;
        int assessed = 0;
        int closed = 0;
        int unacceptable = 0;
        int notAssessed = 0;
        int openActions = 0;
        int overdueActions = 0;

        for (OccurrenceDto row : rows) {
            switch (row.status()) {
                case "REPORTED" -> reported++;
                case "UNDER_REVIEW" -> underReview++;
                case "RISK_ASSESSED", "ACTIONS_OPEN" -> assessed++;
                case "CLOSED" -> closed++;
                default -> { }
            }
            if (row.riskLevel() == null) {
                notAssessed++;
            } else if ("UNACCEPTABLE".equals(row.riskLevel())) {
                unacceptable++;
            }
            openActions += row.openActions();
            overdueActions += (int) row.actions().stream().filter(ActionDto::overdue).count();
        }

        return new SafetyBoardDto(rows, matrix, rows.size(), reported, underReview, assessed,
                closed, unacceptable, openActions, overdueActions, notAssessed,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public OccurrenceDto findOccurrence(UUID tenantId, UUID occurrenceId) {
        Occurrence occurrence = require(tenantId, occurrenceId);
        return withActions(tenantId, List.of(occurrence), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    @Override
    public ReportingSummaryDto findReporting(UUID tenantId, int windowDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime since = today.minusDays(windowDays).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Occurrence> occurrences = occurrenceRepository.findAll(tenantId, null, since);
        List<OccurrenceDto> rows = withActions(tenantId, occurrences, today);

        Map<String, Long> byCategory = occurrences.stream()
                .collect(Collectors.groupingBy(o -> o.getCategory().name(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byMonth = occurrences.stream()
                .collect(Collectors.groupingBy(o -> MONTH.format(o.getOccurredAt()),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byRisk = occurrences.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getRiskLevel() == null ? "NOT_ASSESSED" : o.getRiskLevel().name(),
                        LinkedHashMap::new, Collectors.counting()));

        int filed = (int) occurrences.stream().filter(o -> o.getEccairsExportedAt() != null).count();
        int anonymous = (int) occurrences.stream().filter(Occurrence::isAnonymous).count();

        return new ReportingSummaryDto(rows, byCategory, byMonth, byRisk,
                rows.size(), filed, rows.size() - filed, anonymous,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public PromotionBoardDto findPromotion(UUID tenantId) {
        List<Campaign> campaigns = campaignRepository.findByTenantIdOrderByStartsOnDesc(tenantId);
        Map<UUID, Integer> acks = acknowledgementRepository.countByCampaign(tenantId).stream()
                .collect(Collectors.toMap(CampaignCount::campaignId, CampaignCount::intValue));

        List<Person> people = personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId);

        List<CampaignDto> rows = new ArrayList<>(campaigns.size());
        int running = 0;
        int planned = 0;
        int closed = 0;
        int awaiting = 0;

        for (Campaign campaign : campaigns) {
            int audienceSize = (int) people.stream()
                    .filter(person -> matchesAudience(person, campaign.getAudience()))
                    .count();
            int acknowledged = acks.getOrDefault(campaign.getId(), 0);
            CampaignDto dto = mapper.toDto(campaign, audienceSize, acknowledged);
            rows.add(dto);

            switch (campaign.getStatus()) {
                case "RUNNING" -> running++;
                case "PLANNED" -> planned++;
                case "CLOSED" -> closed++;
                default -> { }
            }
            if (campaign.isAcknowledgementRequired() && !"PLANNED".equals(campaign.getStatus())) {
                awaiting += Math.max(0, audienceSize - acknowledged);
            }
        }

        /* Les cinq premiers indicateurs : c'est ce que la colonne publie, et
           les publier tous transformerait un rappel en tableau de bord. */
        return new PromotionBoardDto(rows,
                overviewService.findIndicators(tenantId).stream().limit(5).toList(),
                JUST_CULTURE,
                running, planned, closed, awaiting,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional
    public OccurrenceDto report(UUID tenantId, ReportOccurrenceCommand command) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (command.occurredAt().isAfter(now)) {
            throw new BusinessRuleException("OCCURRENCE_IN_FUTURE",
                    "An occurrence cannot have happened in the future");
        }

        Occurrence occurrence = new Occurrence();
        occurrence.setTenantId(tenantId);
        occurrence.setReference(nextReference(tenantId, now));
        occurrence.setOccurredAt(command.occurredAt());
        occurrence.setReportedAt(now);
        occurrence.setAnonymous(Boolean.TRUE.equals(command.anonymous()));
        occurrence.setCategory(parseEnum(OccurrenceCategory.class, command.category(), "OCCURRENCE_CATEGORY_UNKNOWN"));
        occurrence.setTitle(command.title().trim());
        occurrence.setNarrative(command.narrative().trim());
        occurrence.setPhaseOfFlight(command.phaseOfFlight());
        occurrence.setStationIcao(command.stationIcao());
        occurrence.setLegId(command.legId());
        occurrence.setEccairsEventType(command.eccairsEventType());
        occurrence.setStatus(OccurrenceStatus.REPORTED);

        if (command.reportedBy() != null) {
            occurrence.setReportedBy(personRepository.findByTenantIdAndId(tenantId, command.reportedBy())
                    .orElseThrow(() -> ResourceNotFoundException.of("Person", command.reportedBy())));
        }
        if (command.aircraftId() != null) {
            occurrence.setAircraft(aircraftRepository.findOneWithType(tenantId, command.aircraftId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", command.aircraftId())));
        }
        return mapper.toDto(occurrenceRepository.save(occurrence), List.of());
    }

    @Override
    @Transactional
    public OccurrenceDto assessRisk(UUID tenantId, UUID occurrenceId, AssessRiskCommand command, UUID actorId) {
        Occurrence occurrence = require(tenantId, occurrenceId);
        List<RiskMatrixCell> cells = matrixRepository.findByTenantIdOrderBySeverityAscProbabilityAsc(tenantId);

        RiskLevel level = riskRule.level(cells, command.severity(), command.probability());
        if (level == null) {
            // The matrix does not cover the pair: that is a gap in the operator
            // manual, and the product says so instead of choosing a level.
            throw new BusinessRuleException("RISK_CELL_UNDEFINED",
                    "The operator matrix defines no level for severity " + command.severity()
                            + " and probability " + command.probability());
        }

        occurrence.setRiskSeverity(command.severity().toUpperCase());
        occurrence.setRiskProbability(command.probability());
        occurrence.setRiskLevel(level);
        occurrence.setRiskAssessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        occurrence.setRiskAssessedBy(actorId);
        if (occurrence.getStatus() == OccurrenceStatus.REPORTED
                || occurrence.getStatus() == OccurrenceStatus.UNDER_REVIEW) {
            occurrence.setStatus(OccurrenceStatus.RISK_ASSESSED);
        }
        Occurrence saved = occurrenceRepository.save(occurrence);
        return withActions(tenantId, List.of(saved), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    @Override
    @Transactional
    public ActionDto addAction(UUID tenantId, UUID occurrenceId, CreateActionCommand command) {
        Occurrence occurrence = require(tenantId, occurrenceId);

        SafetyAction action = new SafetyAction();
        action.setTenantId(tenantId);
        action.setOccurrence(occurrence);
        action.setReference(occurrence.getReference().replace("OCC", "ACT") + "-"
                + (actionRepository.findByOccurrenceIds(tenantId, List.of(occurrenceId)).size() + 1));
        action.setTitle(command.title().trim());
        action.setDetail(command.detail());
        action.setOwnerUserId(command.ownerUserId());
        action.setDueOn(command.dueOn());
        action.setStatus(ActionStatus.OPEN);
        action.setEffectiveness("NOT_ASSESSED");

        if (occurrence.getStatus() == OccurrenceStatus.RISK_ASSESSED) {
            occurrence.setStatus(OccurrenceStatus.ACTIONS_OPEN);
            occurrenceRepository.save(occurrence);
        }
        return mapper.toDto(actionRepository.save(action), LocalDate.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional
    public ActionDto updateAction(UUID tenantId, UUID actionId, UpdateActionCommand command) {
        SafetyAction action = actionRepository.findByTenantIdAndId(tenantId, actionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Safety action", actionId));
        ActionStatus target = parseEnum(ActionStatus.class, command.status(), "ACTION_STATUS_UNKNOWN");

        if (target == ActionStatus.COMPLETED && command.completedOn() == null) {
            throw new BusinessRuleException("ACTION_COMPLETION_DATE_REQUIRED",
                    "A completed action carries the day it was completed");
        }
        action.setStatus(target);
        action.setCompletedOn(target == ActionStatus.COMPLETED ? command.completedOn() : null);
        if (command.effectiveness() != null) {
            action.setEffectiveness(command.effectiveness());
        }
        return mapper.toDto(actionRepository.save(action), LocalDate.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional
    public OccurrenceDto close(UUID tenantId, UUID occurrenceId, CloseOccurrenceCommand command, UUID actorId) {
        Occurrence occurrence = require(tenantId, occurrenceId);
        if (!occurrence.getStatus().canClose()) {
            throw new BusinessRuleException("OCCURRENCE_NOT_ASSESSED",
                    "An occurrence is assessed before it is closed; this one is " + occurrence.getStatus());
        }
        boolean outstanding = actionRepository.findByOccurrenceIds(tenantId, List.of(occurrenceId)).stream()
                .anyMatch(action -> action.getStatus().isOutstanding());
        if (outstanding) {
            throw new BusinessRuleException("OCCURRENCE_ACTIONS_OPEN",
                    "An action is still outstanding: close it, or cancel it with a reason, before closing the occurrence");
        }

        occurrence.setStatus(OccurrenceStatus.CLOSED);
        occurrence.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        occurrence.setClosedBy(actorId);
        occurrence.setNarrative(occurrence.getNarrative() + "\n\nClosure: " + command.conclusion());
        Occurrence saved = occurrenceRepository.save(occurrence);
        return withActions(tenantId, List.of(saved), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    @Override
    @Transactional
    public OccurrenceDto fileWithAuthority(UUID tenantId, UUID occurrenceId, FileWithAuthorityCommand command) {
        Occurrence occurrence = require(tenantId, occurrenceId);
        occurrence.setEccairsReference(command.eccairsReference().trim());
        occurrence.setEccairsExportedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (command.occurrenceClass() != null) {
            occurrence.setEccairsOccurrenceClass(command.occurrenceClass());
        }
        Occurrence saved = occurrenceRepository.save(occurrence);
        return withActions(tenantId, List.of(saved), LocalDate.now(ZoneOffset.UTC)).getFirst();
    }

    @Override
    @Transactional
    public void acknowledgeCampaign(UUID tenantId, UUID campaignId, AcknowledgeCampaignCommand command) {
        Campaign campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> ResourceNotFoundException.of("Campaign", campaignId));
        if (acknowledgementRepository.existsByCampaignIdAndPersonId(campaignId, command.personId())) {
            return;
        }
        Person person = personRepository.findByTenantIdAndId(tenantId, command.personId())
                .orElseThrow(() -> ResourceNotFoundException.of("Person", command.personId()));

        CampaignAcknowledgement acknowledgement = new CampaignAcknowledgement();
        acknowledgement.setTenantId(tenantId);
        acknowledgement.setCampaign(campaign);
        acknowledgement.setPerson(person);
        acknowledgement.setAcknowledgedAt(OffsetDateTime.now(ZoneOffset.UTC));
        acknowledgementRepository.save(acknowledgement);
    }

    @Override
    public List<ActionDto> findOutstandingActions(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return actionRepository.findOutstanding(tenantId).stream()
                .map(action -> mapper.toDto(action, today))
                .toList();
    }

    // ----------------------------------------------------------------

    /** Occurrences with their actions: two statements, never one per row. */
    private List<OccurrenceDto> withActions(UUID tenantId, List<Occurrence> occurrences, LocalDate today) {
        if (occurrences.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ActionDto>> byOccurrence = new HashMap<>();
        for (SafetyAction action : actionRepository.findByOccurrenceIds(
                tenantId, occurrences.stream().map(Occurrence::getId).toList())) {
            byOccurrence.computeIfAbsent(action.getOccurrence().getId(), key -> new ArrayList<>())
                    .add(mapper.toDto(action, today));
        }
        return occurrences.stream()
                .map(occurrence -> mapper.toDto(occurrence,
                        byOccurrence.getOrDefault(occurrence.getId(), List.of())))
                .toList();
    }

    private boolean matchesAudience(Person person, String audience) {
        return switch (audience) {
            case "ALL" -> true;
            case "FLIGHT_CREW" -> person.getMainRole() == CrewRole.CAPTAIN
                    || person.getMainRole() == CrewRole.FIRST_OFFICER;
            case "CABIN" -> person.getMainRole() == CrewRole.CABIN;
            case "MAINTENANCE" -> person.getMainRole() == CrewRole.ENGINEER;
            default -> false;
        };
    }

    private Occurrence require(UUID tenantId, UUID occurrenceId) {
        return occurrenceRepository.findOne(tenantId, occurrenceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Occurrence", occurrenceId));
    }

    private String nextReference(UUID tenantId, OffsetDateTime now) {
        String base = "OCC-" + YEAR.format(now) + "-";
        for (int suffix = 1; suffix < 1000; suffix++) {
            String candidate = base + String.format("%03d", suffix);
            if (occurrenceRepository.findByTenantIdAndReference(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleException("OCCURRENCE_REFERENCE_EXHAUSTED",
                "More than nine hundred and ninety-nine occurrences this year");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String rule) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(rule, "Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
