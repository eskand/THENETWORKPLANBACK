package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.admin.repository.SettingRepository;
import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.safety.domain.Audit;
import com.thenetworkplan.networkplan.safety.domain.AuditFinding;
import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import com.thenetworkplan.networkplan.safety.domain.SafetyAction;
import com.thenetworkplan.networkplan.safety.domain.SpiDefinition;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.AccountabilityDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.AuditDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringSummaryDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.InvestigationDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.RiskByDomainDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SafetyChangeDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.RiskProfileDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SafetyOverviewDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SpiDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.TrendPointDto;
import com.thenetworkplan.networkplan.safety.mapper.SafetyMapper;
import com.thenetworkplan.networkplan.safety.repository.AuditFindingRepository;
import com.thenetworkplan.networkplan.safety.repository.AuditRepository;
import com.thenetworkplan.networkplan.crew.dto.FtlExceedanceDto;
import com.thenetworkplan.networkplan.crew.service.CrewDutyService;
import com.thenetworkplan.networkplan.roster.dto.RosterVersionDto;
import com.thenetworkplan.networkplan.roster.service.RosterService;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.RosterCheckDto;
import com.thenetworkplan.networkplan.safety.repository.InvestigationRecommendationRepository;
import com.thenetworkplan.networkplan.safety.repository.InvestigationRepository;
import com.thenetworkplan.networkplan.safety.repository.InvestigationStepRepository;
import com.thenetworkplan.networkplan.safety.repository.OccurrenceRepository;
import com.thenetworkplan.networkplan.safety.repository.RiskMatrixRepository;
import com.thenetworkplan.networkplan.safety.repository.SafetyActionRepository;
import com.thenetworkplan.networkplan.safety.repository.SafetyChangeRepository;
import com.thenetworkplan.networkplan.safety.repository.SpiDefinitionRepository;
import com.thenetworkplan.networkplan.safety.service.SafetyOverviewService;
import com.thenetworkplan.networkplan.safety.service.SafetyScan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Safety Manager dashboard.
 *
 * <p><b>Nothing on this screen is a stored number.</b> Every count, every
 * indicator and every band is computed from the rows that exist when the screen
 * is opened. That is the difference the audit asked for: the prototype writes
 * its dashboard figures into its own source, which is why it can announce four
 * open audit findings while its own audit list carries five.
 *
 * <p><b>The risk index comes from the operator's matrix.</b> Not from a formula
 * in this file: the twenty-five cells carry both the level and the index, so a
 * screen that bands on the index and a screen that bands on the level cannot
 * disagree. A severity and probability pair the matrix does not cover produces
 * no index at all, and the occurrence is counted as unassessed rather than
 * quietly dropped into the bottom band.
 */
@Service
@Transactional(readOnly = true)
public class SafetyOverviewServiceImpl implements SafetyOverviewService {

    /** ICAO Doc 9859 bands, and the ones the approved prototype displays. */
    private static final int INTOLERABLE_FROM = 15;

    private static final int HIGH_FROM = 10;

    /* Locale pinned : sans elle le libelle du mois suit la langue du serveur, et
       le graphique passe de « Jul » a « juil. » selon la machine qui l'affiche. */
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH);

    /** 28 jours : la plus longue fenetre glissante de l'ORO.FTL.210(a). */
    private static final int ROSTER_CHECK_DAYS = 28;

    private static final java.time.format.DateTimeFormatter ROSTER_MONTH =
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH);

    private final OccurrenceRepository occurrenceRepository;
    private final SafetyActionRepository actionRepository;
    private final RiskMatrixRepository matrixRepository;
    private final AuditRepository auditRepository;
    private final AuditFindingRepository findingRepository;
    private final SafetyChangeRepository changeRepository;
    private final InvestigationRepository investigationRepository;
    private final InvestigationStepRepository stepRepository;
    private final InvestigationRecommendationRepository recommendationRepository;
    private final SpiDefinitionRepository spiRepository;
    private final SettingRepository settingRepository;
    private final SafetyScan safetyScan;
    private final CamoService camoService;
    private final CrewPeopleService crewPeopleService;
    private final CrewDutyService crewDutyService;
    private final RosterService rosterService;
    private final SafetyMapper mapper;

    public SafetyOverviewServiceImpl(OccurrenceRepository occurrenceRepository,
                                     SafetyActionRepository actionRepository,
                                     RiskMatrixRepository matrixRepository,
                                     AuditRepository auditRepository,
                                     AuditFindingRepository findingRepository,
                                     SafetyChangeRepository changeRepository,
                                     InvestigationRepository investigationRepository,
                                    InvestigationStepRepository stepRepository,
                                    InvestigationRecommendationRepository recommendationRepository,
                                     SpiDefinitionRepository spiRepository,
                                     SettingRepository settingRepository,
                                     SafetyScan safetyScan,
                                     CamoService camoService,
                                     CrewPeopleService crewPeopleService,
                                     CrewDutyService crewDutyService,
                                     RosterService rosterService,
                                     SafetyMapper mapper) {
        this.occurrenceRepository = occurrenceRepository;
        this.actionRepository = actionRepository;
        this.matrixRepository = matrixRepository;
        this.auditRepository = auditRepository;
        this.findingRepository = findingRepository;
        this.changeRepository = changeRepository;
        this.investigationRepository = investigationRepository;
        this.stepRepository = stepRepository;
        this.recommendationRepository = recommendationRepository;
        this.spiRepository = spiRepository;
        this.settingRepository = settingRepository;
        this.safetyScan = safetyScan;
        this.camoService = camoService;
        this.crewPeopleService = crewPeopleService;
        this.crewDutyService = crewDutyService;
        this.rosterService = rosterService;
        this.mapper = mapper;
    }

    @Override
    public SafetyOverviewDto findOverview(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth thisMonth = YearMonth.from(today);

        List<Occurrence> occurrences = occurrenceRepository.findAll().stream()
                .filter(o -> tenantId.equals(o.getTenantId()))
                .toList();
        Map<String, Integer> indexByCell = riskIndex(tenantId);

        List<Audit> audits = auditRepository.findByTenantIdOrderByPlannedOnAsc(tenantId);
        List<AuditFinding> findings = findingRepository.findAllWithAudit(tenantId);
        List<SafetyAction> actions = actionRepository.findAll().stream()
                .filter(a -> tenantId.equals(a.getTenantId()))
                .toList();

        MonitoringSummaryDto monitoring = safetyScan.scan(tenantId);

        int thisMonthCount = (int) occurrences.stream()
                .filter(o -> YearMonth.from(o.getOccurredAt().atZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDate()).equals(thisMonth))
                .count();
        int stillOpen = (int) occurrences.stream()
                .filter(o -> !"CLOSED".equals(o.getStatus().name()))
                .count();

        RiskProfileDto profile = riskProfile(occurrences, indexByCell);
        int overdueActions = (int) actions.stream().filter(a -> isOverdue(a, today)).count();
        int openFindings = (int) findings.stream().filter(AuditFinding::isOpen).count();
        int planned = (int) audits.stream().filter(a -> "PLANNED".equals(a.getStatus())).count();

        return new SafetyOverviewDto(
                thisMonthCount,
                stillOpen,
                profile.intolerable(),
                overdueActions,
                openFindings,
                planned,
                monitoring,
                profile,
                trend(occurrences, indexByCell, thisMonth),
                indicators(tenantId, occurrences, actions, today, thisMonth),
                recentOccurrences(occurrences),
                actions.stream()
                        .filter(a -> !"DONE".equals(a.getStatus().name()))
                        .sorted(Comparator.comparing(SafetyAction::getDueOn,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(6)
                        .map(action -> mapper.toDto(action, today))
                        .toList(),
                auditProgramme(audits, findings),
                riskByDomain(occurrences, indexByCell),
                investigations(tenantId, today),
                changes(tenantId),
                rosterCheck(tenantId, today),
                accountability(tenantId, monitoring));
    }

    /**
     * Les indicateurs seuls, sans le balayage.
     *
     * <p>Safety Promotion publie les objectifs dans sa colonne de droite et n'a
     * que faire des constats du balayage, qui interroge six modules. Une
     * methode a part evite de payer ce prix pour cinq lignes de bandeau.
     */
    @Override
    public List<SpiDto> findIndicators(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth month = YearMonth.from(today);
        List<Occurrence> occurrences = occurrenceRepository.findAll().stream()
                .filter(occurrence -> tenantId.equals(occurrence.getTenantId()))
                .toList();
        List<SafetyAction> actions = actionRepository.findAll().stream()
                .filter(action -> tenantId.equals(action.getTenantId()))
                .toList();
        return indicators(tenantId, occurrences, actions, today, month);
    }

    @Override
    public MonitoringSummaryDto runScan(UUID tenantId) {
        return safetyScan.scan(tenantId);
    }

    /* ---------- risk ---------- */

    /** The operator's twenty-five cells, keyed by the pair they answer for. */
    private Map<String, Integer> riskIndex(UUID tenantId) {
        Map<String, Integer> index = new HashMap<>();
        for (RiskMatrixCell cell : matrixRepository.findAll()) {
            if (tenantId.equals(cell.getTenantId()) || cell.getTenantId() == null) {
                index.put(cell.getSeverity() + cell.getProbability(), (int) cell.getRiskIndex());
            }
        }
        return index;
    }

    private Integer indexOf(Occurrence occurrence, Map<String, Integer> cells) {
        if (occurrence.getRiskSeverity() == null || occurrence.getRiskProbability() == null) {
            return null;
        }
        return cells.get(occurrence.getRiskSeverity() + occurrence.getRiskProbability());
    }

    private RiskProfileDto riskProfile(List<Occurrence> occurrences, Map<String, Integer> cells) {
        int intolerable = 0;
        int high = 0;
        int tolerable = 0;
        int unassessed = 0;

        for (Occurrence occurrence : occurrences) {
            Integer index = indexOf(occurrence, cells);
            if (index == null) {
                unassessed++;
            } else if (index >= INTOLERABLE_FROM) {
                intolerable++;
            } else if (index >= HIGH_FROM) {
                high++;
            } else {
                tolerable++;
            }
        }
        return new RiskProfileDto(occurrences.size(), intolerable, high, tolerable, unassessed);
    }

    /**
     * Six months of occurrences, and how many of them were high or worse.
     *
     * <p>Every month is counted from the register, including the older ones.
     * The prototype computes three months live and reads the other three from a
     * stored monthly history — which is why its chart can disagree with its own
     * occurrence list.
     */
    private List<TrendPointDto> trend(List<Occurrence> occurrences, Map<String, Integer> cells,
                                      YearMonth thisMonth) {
        List<TrendPointDto> points = new ArrayList<>(6);
        for (int back = 5; back >= 0; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            List<Occurrence> inMonth = occurrences.stream()
                    .filter(o -> YearMonth.from(o.getOccurredAt().atZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()).equals(month))
                    .toList();
            int severe = (int) inMonth.stream()
                    .map(o -> indexOf(o, cells))
                    .filter(index -> index != null && index >= HIGH_FROM)
                    .count();
            points.add(new TrendPointDto(month.atDay(1).format(MONTH), inMonth.size(), severe));
        }
        return points;
    }

    /** Initial against residual risk, by the part of the operation it belongs to. */
    /**
     * Les enquetes, ouvertes en premier.
     *
     * <p>{@code overdue} se calcule : une enquete est en retard quand sa date
     * cible est passee et qu elle n est pas close. Un drapeau stocke serait
     * vrai le jour ou on l ecrit et faux le lendemain.
     */
    private List<InvestigationDto> investigations(UUID tenantId, LocalDate today) {
        /* Deux requetes pour toute la page, pas deux par enquete : la chaine et
           les recommandations arrivent groupees et se rattachent en memoire. */
        Map<UUID, List<String>> steps = new java.util.HashMap<>();
        stepRepository.findAllForTenant(tenantId).forEach(step ->
                steps.computeIfAbsent(step.getInvestigation().getId(), key -> new ArrayList<>())
                        .add(step.getStatement()));

        Map<UUID, List<String>> recommendations = new java.util.HashMap<>();
        recommendationRepository.findAllForTenant(tenantId).forEach(recommendation ->
                recommendations.computeIfAbsent(recommendation.getInvestigation().getId(),
                        key -> new ArrayList<>()).add(recommendation.getRecommendation()));

        return investigationRepository.findByTenantIdOrderByOpenedOnDesc(tenantId).stream()
                .map(investigation -> new InvestigationDto(
                        investigation.getId(), investigation.getReference(),
                        investigation.getOccurrence() == null
                                ? null : investigation.getOccurrence().getReference(),
                        investigation.getTitle(), investigation.getInvestigatorName(),
                        investigation.getOpenedOn(), investigation.getTargetOn(),
                        investigation.getClosedOn(), investigation.getStatus(),
                        investigation.getMethod(), investigation.getRootCause(),
                        investigation.getContributingFactors(),
                        investigation.getClosedOn() == null
                                && investigation.getTargetOn() != null
                                && investigation.getTargetOn().isBefore(today),
                        investigation.getProgressPercent(),
                        steps.getOrDefault(investigation.getId(), List.of()),
                        recommendations.getOrDefault(investigation.getId(), List.of())))
                .toList();
    }

    /**
     * Le roster publie, passe au moteur FTL.
     *
     * <p><b>Vingt-huit jours, pas une fenetre arbitraire.</b> Les plafonds de
     * l'ORO.FTL.210(a) se comptent sur 7 et 28 jours glissants : lire moins
     * loin annoncerait « aucun depassement » pour la seule raison qu'on n'a
     * pas regarde assez loin.
     *
     * <p><b>Le meme moteur que la feuille FTL et le rapport des violations.</b>
     * Trois copies de l'arithmetique seraient trois reponses, et le premier
     * audit d'autorite les trouverait toutes les trois — l'echec precis qu'un
     * SMS existe pour eviter.
     *
     * <p>Le nombre de gardes controlees voyage avec le verdict : « aucun
     * depassement sur zero garde » n'est pas une assurance.
     */
    private RosterCheckDto rosterCheck(UUID tenantId, LocalDate today) {
        /* Ce sont les MOIS PUBLIES qui sont controles, pas une fenetre glissante
           choisie ici. L'annexe annonce « across September 2026 » parce qu'elle
           a scanne le roster publie de septembre ; annoncer deux mois parce que
           la fenetre en chevauche deux dirait au dirigeant responsable qu'on a
           verifie un mois qui n'est peut-etre meme pas publie. */
        List<RosterVersionDto> published = rosterService.findVersions(tenantId).stream()
                .filter(version -> "PUBLISHED".equals(version.status()))
                .toList();

        if (published.isEmpty()) {
            return new RosterCheckDto(List.of(), 0, 0, 0, List.of());
        }

        LocalDate from = published.stream().map(RosterVersionDto::periodStart)
                .min(LocalDate::compareTo).orElse(today);
        LocalDate to = published.stream().map(RosterVersionDto::periodEnd)
                .max(LocalDate::compareTo).orElse(today);

        Set<YearMonth> covered = new java.util.TreeSet<>();
        published.forEach(version -> {
            for (YearMonth month = YearMonth.from(version.periodStart());
                    !month.isAfter(YearMonth.from(version.periodEnd())); month = month.plusMonths(1)) {
                covered.add(month);
            }
        });
        List<String> months = covered.stream().map(month -> month.format(ROSTER_MONTH)).toList();

        /* Le moteur lit 27 jours avant le debut publie pour que le plafond
           glissant de 28 jours ait de quoi se calculer — mais seuls les
           depassements qui TOMBENT dans un mois publie sont rapportes. Compter
           ceux d'avant ferait porter au roster publie des journees qu'il ne
           couvre pas. */
        List<FtlExceedanceDto> exceedances = crewDutyService
                .findExceedances(tenantId, from.minusDays(ROSTER_CHECK_DAYS - 1L), to).stream()
                .filter(breach -> covered.contains(YearMonth.from(breach.day())))
                .toList();

        // Les quatre equipages les plus concernes : au-dela, la banniere
        // devient une liste que personne ne lit.
        Map<String, Long> byCrew = new LinkedHashMap<>();
        exceedances.forEach(breach -> byCrew.merge(breach.crewName(), 1L, Long::sum));
        List<String> worst = byCrew.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(4)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .toList();

        int duties = crewDutyService.findDuties(tenantId, from, to).size();
        int days = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        return new RosterCheckDto(months, days, duties, exceedances.size(), worst);
    }

    /**
     * Les changements sous gestion du changement.
     *
     * <p>{@code active} se derive : un changement est actif tant qu il n est pas
     * clos. Le stocker aurait donne un drapeau a maintenir en plus de la date.
     */
    private List<SafetyChangeDto> changes(UUID tenantId) {
        return changeRepository.findByTenantIdOrderByRaisedOnDesc(tenantId).stream()
                .map(change -> new SafetyChangeDto(
                        change.getId(), change.getReference(), change.getTitle(),
                        change.getDescription(), change.getDomain(), change.getRaisedOn(),
                        change.getEffectiveOn(), change.getStatus(), change.getOwnerName(),
                        change.getInitialIndex(), change.getResidualIndex(),
                        change.getMitigation(), change.getClosedOn() == null))
                .toList();
    }

    private List<RiskByDomainDto> riskByDomain(List<Occurrence> occurrences, Map<String, Integer> cells) {
        Map<String, int[]> byDomain = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            Integer index = indexOf(occurrence, cells);
            if (index == null) {
                continue;
            }
            String domain = occurrence.getCategory().name();
            int[] totals = byDomain.computeIfAbsent(domain, key -> new int[3]);
            totals[0] += index;
            // Residual: the risk left once every action on the occurrence is
            // closed. An occurrence still carrying open actions has not been
            // mitigated, so its residual is its initial.
            totals[1] += "CLOSED".equals(occurrence.getStatus().name()) ? Math.max(1, index / 2) : index;
            totals[2]++;
        }
        return byDomain.entrySet().stream()
                .map(entry -> new RiskByDomainDto(entry.getKey(),
                        label(entry.getKey()),
                        entry.getValue()[0] / entry.getValue()[2],
                        entry.getValue()[1] / entry.getValue()[2]))
                .sorted(Comparator.comparingInt(RiskByDomainDto::initialIndex).reversed())
                .toList();
    }

    private String label(String category) {
        String lower = category.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /* ---------- indicators ---------- */

    /**
     * The eight indicators, measured.
     *
     * <p>Five are answered from the safety register itself; three are asked of
     * the module that owns the answer — crew currency of the crew module, MEL
     * items and airworthiness of CAMO. A value of null means nothing can
     * measure it yet, which the screen shows as such rather than as zero.
     */
    private List<SpiDto> indicators(UUID tenantId, List<Occurrence> occurrences,
                                    List<SafetyAction> actions, LocalDate today, YearMonth month) {
        List<Occurrence> inMonth = occurrences.stream()
                .filter(o -> YearMonth.from(o.getOccurredAt().atZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDate()).equals(month))
                .toList();
        Map<String, Integer> cells = riskIndex(tenantId);

        Map<String, BigDecimal> values = new HashMap<>();

        // SPI-01 needs an exposure figure — sectors flown this month. Until the
        // overview is given one, the rate cannot be honestly stated, and a null
        // says so where a zero would claim a perfect month.
        values.put("SPI-02", BigDecimal.valueOf(inMonth.stream()
                .map(o -> indexOf(o, cells))
                .filter(index -> index != null && index >= HIGH_FROM)
                .count()));

        long closable = occurrences.stream().filter(o -> o.getClosedAt() != null).count();
        if (closable > 0) {
            long quick = occurrences.stream()
                    .filter(o -> o.getClosedAt() != null)
                    .filter(o -> java.time.Duration.between(o.getReportedAt(), o.getClosedAt()).toDays() <= 30)
                    .count();
            values.put("SPI-03", BigDecimal.valueOf(quick * 1000L / closable)
                    .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP));
        }

        values.put("SPI-04", BigDecimal.valueOf(actions.stream().filter(a -> isOverdue(a, today)).count()));

        /* SPI-05 : la part de l'equipage dont aucun document n'est perime. Le
           denominateur est l'equipage ACTIF, pas la liste des echeances — sinon
           un equipage dont rien n'expire bientot donnerait une division par zero
           et l'indicateur disparaitrait le jour ou tout va bien. */
        List<PersonDto> crew = crewPeopleService.findAll(tenantId, null, null, true);
        if (!crew.isEmpty()) {
            long current = crew.stream()
                    .filter(person -> !"EXPIRED".equals(person.documentStatus()))
                    .count();
            values.put("SPI-05", BigDecimal.valueOf(current * 100L)
                    .divide(BigDecimal.valueOf(crew.size()), 0, RoundingMode.HALF_UP));
        }

        List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
        if (!fleet.isEmpty()) {
            values.put("SPI-06", BigDecimal.valueOf(fleet.stream()
                    .mapToInt(FleetStatusRowDto::openMelItems).sum()));
            long airworthy = fleet.stream()
                    .filter(row -> !"EXPIRED".equals(row.arcVerdict()) && !"NONE".equals(row.arcVerdict()))
                    .filter(row -> row.overdueTasks() == 0)
                    .count();
            values.put("SPI-07", BigDecimal.valueOf(airworthy * 100L)
                    .divide(BigDecimal.valueOf(fleet.size()), 0, RoundingMode.HALF_UP));
        }

        values.put("SPI-08", BigDecimal.valueOf(inMonth.stream().filter(Occurrence::isAnonymous).count()));

        return spiRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .map(definition -> toDto(definition, values.get(definition.getCode())))
                .toList();
    }

    private SpiDto toDto(SpiDefinition definition, BigDecimal value) {
        return new SpiDto(
                definition.getCode(), definition.getName(), definition.getUnit(),
                definition.getDomain(), value, definition.getTargetValue(),
                definition.getAlertValue(), definition.getDirection(),
                definition.meetsTarget(value), definition.breachesAlert(value),
                definition.getComputedBy());
    }

    /* ---------- lists ---------- */

    private List<com.thenetworkplan.networkplan.safety.dto.SafetyDtos.OccurrenceDto>
            recentOccurrences(List<Occurrence> occurrences) {
        return occurrences.stream()
                .sorted(Comparator.comparing(Occurrence::getOccurredAt).reversed())
                .limit(6)
                .map(o -> mapper.toDto(o, List.of()))
                .toList();
    }

    private List<AuditDto> auditProgramme(List<Audit> audits, List<AuditFinding> findings) {
        Map<UUID, List<AuditFinding>> byAudit = new HashMap<>();
        findings.forEach(finding -> byAudit
                .computeIfAbsent(finding.getAudit().getId(), key -> new ArrayList<>())
                .add(finding));

        return audits.stream().map(audit -> {
            List<AuditFinding> own = byAudit.getOrDefault(audit.getId(), List.of());
            return new AuditDto(audit.getId(), audit.getReference(), audit.getName(),
                    audit.getStandard(), audit.getScope(), audit.getAuditor(),
                    audit.isExternalAudit(), audit.getPlannedOn(), audit.getConductedOn(),
                    audit.getClosedOn(), audit.getStatus(), audit.getScorePercent(),
                    own.size(), (int) own.stream().filter(AuditFinding::isOpen).count());
        }).toList();
    }

    /** Who answers for the system. Read from the settings, where it is declared. */
    private AccountabilityDto accountability(UUID tenantId, MonitoringSummaryDto monitoring) {
        Map<String, String> settings = new HashMap<>();
        settingRepository.findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId)
                .forEach(setting -> settings.put(setting.getSettingKey(), setting.getSettingValue()));

        int activeChanges = (int) changeRepository.findByTenantIdOrderByRaisedOnDesc(tenantId).stream()
                .filter(change -> change.isActive())
                .count();

        return new AccountabilityDto(
                settings.get("safety.accountableManager"),
                settings.get("safety.safetyManager"),
                settings.getOrDefault("general.occName", "The Network Plan Airlines"),
                settings.get("safety.aocReference"),
                activeChanges,
                monitoring.scannedAt());
    }

    /** Past its due date and not done. Derived, as everywhere else. */
    private boolean isOverdue(SafetyAction action, LocalDate on) {
        return !"DONE".equals(action.getStatus().name())
                && action.getDueOn() != null
                && action.getDueOn().isBefore(on);
    }
}
