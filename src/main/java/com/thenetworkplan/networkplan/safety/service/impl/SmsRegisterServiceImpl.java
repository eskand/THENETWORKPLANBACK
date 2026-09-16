package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.safety.domain.Hazard;
import com.thenetworkplan.networkplan.safety.domain.HazardControl;
import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.Rex;
import com.thenetworkplan.networkplan.safety.domain.RexLesson;
import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import com.thenetworkplan.networkplan.safety.domain.SafetyNotification;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AnswerQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AskQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.ControlDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.HazardDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.HazardRegisterDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.MatrixCellDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.NotificationCentreDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.NotificationDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.QueryDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.RexDto;
import com.thenetworkplan.networkplan.safety.repository.HazardControlRepository;
import com.thenetworkplan.networkplan.safety.repository.HazardRepository;
import com.thenetworkplan.networkplan.safety.repository.OccurrenceRepository;
import com.thenetworkplan.networkplan.safety.repository.RexLessonRepository;
import com.thenetworkplan.networkplan.safety.repository.RexRepository;
import com.thenetworkplan.networkplan.safety.repository.RiskMatrixRepository;
import com.thenetworkplan.networkplan.safety.repository.SafetyNotificationRepository;
import com.thenetworkplan.networkplan.safety.service.SmsRegisterService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * The hazard register, the experience library and the notification centre.
 *
 * <p><b>The matrix comes from the operator's own table.</b> {@code
 * safety.risk_matrix} holds what a C3 is worth and what response it demands.
 * Reproducing that scale in Java would give the screen a second opinion, and
 * the two would disagree the first time the operator revised one of them.
 *
 * <p><b>Nothing here stores an index.</b> Severity times likelihood is
 * arithmetic; a stored product drifts from its factors at the first
 * reassessment, and it is the figure an auditor reads first.
 */
@Service
@Transactional(readOnly = true)
public class SmsRegisterServiceImpl implements SmsRegisterService {

    private static final List<String> SEVERITIES = List.of("A", "B", "C", "D", "E");

    private final HazardRepository hazardRepository;
    private final HazardControlRepository controlRepository;
    private final RiskMatrixRepository matrixRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final RexRepository rexRepository;
    private final RexLessonRepository lessonRepository;
    private final SafetyNotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SmsRegisterServiceImpl(HazardRepository hazardRepository,
                                  HazardControlRepository controlRepository,
                                  RiskMatrixRepository matrixRepository,
                                  OccurrenceRepository occurrenceRepository,
                                  RexRepository rexRepository,
                                  RexLessonRepository lessonRepository,
                                  SafetyNotificationRepository notificationRepository) {
        this.hazardRepository = hazardRepository;
        this.controlRepository = controlRepository;
        this.matrixRepository = matrixRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.rexRepository = rexRepository;
        this.lessonRepository = lessonRepository;
        this.notificationRepository = notificationRepository;
    }

    /* ─────────────────────────────────────────── the hazard register ── */

    @Override
    public HazardRegisterDto findRegister(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Hazard> hazards = hazardRepository.findByTenantIdOrderByReference(tenantId);

        Map<UUID, List<HazardControl>> controls = controlRepository
                .findByTenantIdOrderBySortOrder(tenantId).stream()
                .collect(Collectors.groupingBy(HazardControl::getHazardId));

        Map<UUID, List<String>> links = linkedOccurrences(tenantId);
        Map<String, RiskMatrixCell> matrix = matrixRepository
                .findByTenantIdOrderBySeverityAscProbabilityAsc(tenantId).stream()
                .collect(Collectors.toMap(
                        cell -> cell.getSeverity() + cell.getProbability(),
                        cell -> cell, (first, second) -> first));

        List<HazardDto> dtos = hazards.stream()
                .map(hazard -> toDto(hazard,
                        controls.getOrDefault(hazard.getId(), List.of()),
                        links.getOrDefault(hazard.getId(), List.of()),
                        matrix, today))
                .sorted(Comparator.comparingInt(HazardDto::indexResidual).reversed())
                .toList();

        return new HazardRegisterDto(
                dtos,
                matrixOf(matrix, hazards, false),
                matrixOf(matrix, hazards, true),
                (int) dtos.stream().filter(h -> "open".equals(h.status())).count(),
                (int) dtos.stream().filter(h -> "mitigating".equals(h.status())).count(),
                (int) dtos.stream().filter(h -> "monitored".equals(h.status())).count(),
                (int) dtos.stream().filter(HazardDto::reviewOverdue).count(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Which occurrences each hazard was identified from.
     *
     * <p>Read as references rather than ids: the register shows
     * "OCC-2026-0112", and resolving a uuid on the screen would be a request
     * per row for a string the join already has.
     */
    private Map<UUID, List<String>> linkedOccurrences(UUID tenantId) {
        Map<UUID, List<String>> out = new HashMap<>();
        entityManager.createNativeQuery("""
                        select l.hazard_id, o.reference
                          from safety.hazard_occurrences l
                          join safety.occurrences o on o.id = l.occurrence_id
                         where o.tenant_id = ?1
                         order by o.reference
                        """)
                .setParameter(1, tenantId)
                .getResultList()
                .forEach(row -> {
                    Object[] cells = (Object[]) row;
                    out.computeIfAbsent((UUID) cells[0], key -> new ArrayList<>())
                            .add((String) cells[1]);
                });
        return out;
    }

    private HazardDto toDto(Hazard hazard, List<HazardControl> controls,
                            List<String> links, Map<String, RiskMatrixCell> matrix,
                            LocalDate today) {
        Long daysToReview = hazard.getReviewOn() == null
                ? null
                : ChronoUnit.DAYS.between(today, hazard.getReviewOn());

        return new HazardDto(
                hazard.getId(), hazard.getReference(), hazard.getHazard(),
                hazard.getConsequence(), hazard.getDomain(), hazard.getCategory(),
                hazard.getIdentification(),
                hazard.getSeverityInitial(), hazard.getLikelihoodInitial(), hazard.initialIndex(),
                hazard.getSeverityResidual(), hazard.getLikelihoodResidual(), hazard.residualIndex(),
                hazard.reductionPercent(),
                band(matrix, hazard.getSeverityInitial(), hazard.getLikelihoodInitial()),
                band(matrix, hazard.getSeverityResidual(), hazard.getLikelihoodResidual()),
                hazard.getOwner(), hazard.getReviewOn(), daysToReview,
                daysToReview != null && daysToReview < 0,
                hazard.getStatus(), hazard.getNotes(),
                controls.stream()
                        .map(control -> new ControlDto(control.getId(), control.getDescription(),
                                control.getControlType(), control.getOwner(), control.getStatus()))
                        .toList(),
                (int) controls.stream().filter(c -> "in-place".equals(c.getStatus())).count(),
                links);
    }

    private String band(Map<String, RiskMatrixCell> matrix, String severity, int likelihood) {
        RiskMatrixCell cell = matrix.get(severity + likelihood);
        return cell == null ? null : cell.getRiskLevel().name();
    }

    /** The whole 5×5, with how many hazards sit in each cell. */
    private List<MatrixCellDto> matrixOf(Map<String, RiskMatrixCell> matrix,
                                         List<Hazard> hazards, boolean residual) {
        Map<String, Long> population = hazards.stream().collect(Collectors.groupingBy(
                hazard -> residual
                        ? hazard.getSeverityResidual() + hazard.getLikelihoodResidual()
                        : hazard.getSeverityInitial() + hazard.getLikelihoodInitial(),
                Collectors.counting()));

        List<MatrixCellDto> out = new ArrayList<>();
        for (String severity : SEVERITIES) {
            for (int likelihood = 1; likelihood <= 5; likelihood++) {
                RiskMatrixCell cell = matrix.get(severity + likelihood);
                out.add(new MatrixCellDto(
                        severity, likelihood,
                        Hazard.severityValue(severity) * likelihood,
                        cell == null ? null : cell.getRiskLevel().name(),
                        colourOf(cell),
                        cell == null ? null : cell.getActionRequired(),
                        population.getOrDefault(severity + likelihood, 0L).intValue()));
            }
        }
        return out;
    }

    /** The three bands of ICAO Doc 9859, in the platform's own colours. */
    private String colourOf(RiskMatrixCell cell) {
        if (cell == null) {
            return "#8a99b3";
        }
        return switch (cell.getRiskLevel().name()) {
            case "UNACCEPTABLE" -> "#C0392B";
            case "TOLERABLE" -> "#E0C22A";
            default -> "#1f9d5c";
        };
    }

    @Override
    @Transactional
    public HazardRegisterDto recordReview(UUID tenantId, UUID hazardId, int cycleDays) {
        Hazard hazard = hazardRepository.findByTenantIdAndId(tenantId, hazardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Hazard", hazardId));
        hazard.setReviewOn(LocalDate.now(ZoneOffset.UTC).plusDays(cycleDays));
        hazardRepository.save(hazard);
        return findRegister(tenantId);
    }

    /* ────────────────────────────────────── the experience library ── */

    @Override
    public List<RexDto> findRexLibrary(UUID tenantId, String reader) {
        Map<UUID, List<RexLesson>> lessons = lessonRepository.findAllByOrderBySortOrder().stream()
                .collect(Collectors.groupingBy(RexLesson::getRexId));

        Map<UUID, Long> reads = new HashMap<>();
        Map<UUID, Boolean> mine = new HashMap<>();
        entityManager.createNativeQuery(
                        "select rex_id, count(*), bool_or(reader = ?1) from safety.rex_reads group by rex_id")
                .setParameter(1, reader == null ? "" : reader)
                .getResultList()
                .forEach(row -> {
                    Object[] cells = (Object[]) row;
                    reads.put((UUID) cells[0], ((Number) cells[1]).longValue());
                    mine.put((UUID) cells[0], Boolean.TRUE.equals(cells[2]));
                });

        return rexRepository.findByTenantIdOrderByPublishedOnDesc(tenantId).stream()
                .map(rex -> new RexDto(
                        rex.getId(), rex.getReference(), rex.getTitle(), rex.getCategory(),
                        rex.getPhase(), rex.getAircraftType(), rex.getLocation(),
                        rex.getNarrative(), rex.getRecommendation(),
                        rex.getAuthorName(), rex.getAuthorRole(),
                        rex.getAttribution(), rex.getScope(), rex.getPublishedOn(), rex.getStatus(),
                        lessons.getOrDefault(rex.getId(), List.of()).stream()
                                .map(RexLesson::getLesson).toList(),
                        reads.getOrDefault(rex.getId(), 0L).intValue(),
                        Boolean.TRUE.equals(mine.get(rex.getId()))))
                .toList();
    }

    /**
     * Record that somebody read a report.
     *
     * <p>Who, not how many. A counter that does not know who read cannot say
     * who has not — and that is the only question a safety promotion function
     * actually needs answered.
     */
    @Override
    @Transactional
    public List<RexDto> markRexRead(UUID tenantId, UUID rexId, String reader) {
        if (reader == null || reader.isBlank()) {
            throw new BusinessRuleException("REX_READER_REQUIRED",
                    "A read has to be recorded against a reader");
        }
        rexRepository.findByTenantIdAndId(tenantId, rexId)
                .orElseThrow(() -> ResourceNotFoundException.of("REX", rexId));

        entityManager.createNativeQuery("""
                        insert into safety.rex_reads (rex_id, reader)
                        values (?1, ?2)
                        on conflict (rex_id, reader) do nothing
                        """)
                .setParameter(1, rexId)
                .setParameter(2, reader.trim())
                .executeUpdate();

        return findRexLibrary(tenantId, reader);
    }

    /* ─────────────────────────────────── questions to the reporter ── */

    @Override
    public List<QueryDto> findQueries(UUID tenantId, String reporter, boolean openOnly) {
        return occurrenceRepository.findAll(tenantId, null, OffsetDateTime.now(ZoneOffset.UTC)
                        .minusYears(5)).stream()
                .filter(occurrence -> occurrence.getQueryText() != null)
                .filter(occurrence -> !openOnly || occurrence.getQueryAnswer() == null)
                .filter(occurrence -> reporter == null || reporter.isBlank()
                        || reporter.equals(occurrence.getReporterName()))
                .map(this::toQueryDto)
                .sorted(Comparator.comparing(QueryDto::askedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private QueryDto toQueryDto(Occurrence occurrence) {
        return new QueryDto(
                occurrence.getId(), occurrence.getReference(), occurrence.getTitle(),
                occurrence.getOccurredAt(), occurrence.getQueryText(),
                occurrence.getQueryAskedAt(), occurrence.getQueryAskedBy(),
                occurrence.getQueryAnswer(), occurrence.getQueryAnsweredAt());
    }

    @Override
    @Transactional
    public QueryDto askQuery(UUID tenantId, UUID occurrenceId, AskQueryCommand command) {
        Occurrence occurrence = occurrence(tenantId, occurrenceId);
        if (occurrence.getQueryText() != null && occurrence.getQueryAnswer() == null) {
            throw new BusinessRuleException("QUERY_ALREADY_OPEN",
                    "A question is already open on " + occurrence.getReference());
        }
        occurrence.setQueryText(command.question().trim());
        occurrence.setQueryAskedAt(OffsetDateTime.now(ZoneOffset.UTC));
        occurrence.setQueryAskedBy(command.askedBy().trim());
        occurrence.setQueryAnswer(null);
        occurrence.setQueryAnsweredAt(null);
        occurrenceRepository.save(occurrence);

        notify(tenantId, "occurrence", "Question on " + occurrence.getReference(),
                command.question().trim(), "medium", occurrence.getReference());

        return toQueryDto(occurrence);
    }

    @Override
    @Transactional
    public QueryDto answerQuery(UUID tenantId, UUID occurrenceId, AnswerQueryCommand command) {
        Occurrence occurrence = occurrence(tenantId, occurrenceId);
        if (occurrence.getQueryText() == null) {
            throw new BusinessRuleException("NO_QUERY",
                    "No question is open on " + occurrence.getReference());
        }
        occurrence.setQueryAnswer(command.answer().trim());
        occurrence.setQueryAnsweredAt(OffsetDateTime.now(ZoneOffset.UTC));
        occurrenceRepository.save(occurrence);

        notify(tenantId, "occurrence", "Reporter answered on " + occurrence.getReference(),
                command.answer().trim(), "info", occurrence.getReference());

        return toQueryDto(occurrence);
    }

    private Occurrence occurrence(UUID tenantId, UUID id) {
        return occurrenceRepository.findOne(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("Occurrence", id));
    }

    /* ───────────────────────────────────── the notification centre ── */

    @Override
    public NotificationCentreDto findNotifications(UUID tenantId) {
        return new NotificationCentreDto(
                notificationRepository.findByTenantIdOrderByAtDesc(tenantId).stream()
                        .map(this::toDto).toList(),
                notificationRepository.countByTenantIdAndReadAtIsNull(tenantId),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private NotificationDto toDto(SafetyNotification notification) {
        return new NotificationDto(
                notification.getId(), notification.getAt(), notification.getKind(),
                notification.getTitle(), notification.getBody(), notification.getSeverity(),
                notification.getEntityRef(), notification.getDomain(), notification.isRead());
    }

    @Override
    @Transactional
    public NotificationCentreDto markRead(UUID tenantId, UUID notificationId) {
        SafetyNotification notification = notificationRepository
                .findByTenantIdAndId(tenantId, notificationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        if (!notification.isRead()) {
            notification.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
            notificationRepository.save(notification);
        }
        return findNotifications(tenantId);
    }

    @Override
    @Transactional
    public NotificationCentreDto markAllRead(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        notificationRepository.findByTenantIdOrderByAtDesc(tenantId).stream()
                .filter(notification -> !notification.isRead())
                .forEach(notification -> {
                    notification.setReadAt(now);
                    notificationRepository.save(notification);
                });
        return findNotifications(tenantId);
    }

    private void notify(UUID tenantId, String kind, String title, String body,
                        String severity, String entityRef) {
        SafetyNotification notification = new SafetyNotification();
        notification.setTenantId(tenantId);
        notification.setAt(OffsetDateTime.now(ZoneOffset.UTC));
        notification.setKind(kind);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setSeverity(severity);
        notification.setEntityRef(entityRef);
        notificationRepository.save(notification);
    }
}
