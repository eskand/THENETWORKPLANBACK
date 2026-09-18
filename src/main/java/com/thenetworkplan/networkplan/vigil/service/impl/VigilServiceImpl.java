package com.thenetworkplan.networkplan.vigil.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.config.VigilProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import com.thenetworkplan.networkplan.vigil.domain.VigilAlert;
import com.thenetworkplan.networkplan.vigil.domain.VigilAlertStatus;
import com.thenetworkplan.networkplan.vigil.domain.VigilSeverity;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.AskVigilCommand;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAlertDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAnswerDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilContributorDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilCountsDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilFleetDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilFlightDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilPanelDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilUtilisationDto;
import com.thenetworkplan.networkplan.vigil.repository.VigilAlertRepository;
import com.thenetworkplan.networkplan.vigil.service.VigilService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VIGIL, cote serveur.
 *
 * <p>La structure est celle de l'annexe, section par section : DATA (les
 * lectures), RULES (les neuf regles deterministes que nos donnees portent),
 * RISK (le score 0-100 et sa decomposition), ALERTS (identite, mise a jour,
 * resolution automatique, cycle de vie), FLEET (photo de flotte et pression de
 * capacite), AGENT (le routeur d'intentions de {@code askLocal}). Chaque bloc
 * cite la ligne du prototype qu'il reprend.
 *
 * <p><b>Ce qui n'est PAS porte, et pourquoi c'est dit plutot que simule.</b>
 * Le moteur ML de l'annexe (section 3) s'entrainait sur un historique
 * regenere par le meme generateur deterministe que la Timeline — c'est-a-dire
 * sur ses propres donnees de demonstration. Il n'y a pas d'historique de
 * retards reels en base a ce jour ; un modele entraine sur rien rendrait des
 * pourcentages qui ressemblent a des previsions. Les regles {@code RANGE_LIMIT}
 * (pas de moteur range/payload), {@code DUTY_LOW} (pas de FDP restant par
 * personne), {@code ROTATION_BREAK} (positions non calculees) et
 * {@code REVISION_OPEN} (pas de drapeau de revision) attendent leur source. Le
 * panneau les nomme quand on lui pose la question.
 */
@Service
@Transactional(readOnly = true)
public class VigilServiceImpl implements VigilService {

    private static final String AUTO_RESOLVED = "auto — condition no longer observed";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> RISK_CATEGORIES =
            List.of("Permits", "Services", "Crew", "Aircraft", "Maintenance", "Timeline", "Data Quality");

    private final LegService legService;
    private final AircraftService aircraftService;
    private final CrewAssignmentService crewAssignmentService;
    private final PermitService permitService;
    private final GroundServiceService groundServiceService;
    private final VigilAlertRepository alertRepository;
    private final VigilProperties vigil;
    private final OpsProperties ops;

    /** {@code CORE.lastCtxs} — la derniere lecture, par tenant, pour l'agent. */
    private final Map<UUID, Snapshot> lastScan = new ConcurrentHashMap<>();

    public VigilServiceImpl(LegService legService,
                            AircraftService aircraftService,
                            CrewAssignmentService crewAssignmentService,
                            PermitService permitService,
                            GroundServiceService groundServiceService,
                            VigilAlertRepository alertRepository,
                            VigilProperties vigil,
                            OpsProperties ops) {
        this.legService = legService;
        this.aircraftService = aircraftService;
        this.crewAssignmentService = crewAssignmentService;
        this.permitService = permitService;
        this.groundServiceService = groundServiceService;
        this.alertRepository = alertRepository;
        this.vigil = vigil;
        this.ops = ops;
    }

    /* ═══════════════════════ CORE — le balayage (l. 98784) ═══════════════ */

    @Override
    @Transactional
    public VigilPanelDto panel(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();

        List<LegDto> legs = legService.findProgramme(tenantId, today);
        List<UUID> legIds = legs.stream().map(LegDto::id).toList();
        Map<UUID, LegServicesSummary> services = groundServiceService.summariseByLegIds(tenantId, legIds);
        Map<UUID, LegPermitsSummary> permits = permitService.summariseByLegIds(tenantId, legIds);
        Map<UUID, LegCrewDto> crew = crewAssignmentService.findByLegIds(tenantId, legIds, today);
        Map<UUID, List<MelItemDto>> mel = aircraftService.findOpenMelByAircraft(tenantId);
        List<AircraftDto> fleet = aircraftService.findFleet(tenantId);

        Map<UUID, List<LegDto>> byAircraft = legs.stream()
                .sorted(Comparator.comparing(LegDto::std))
                .collect(Collectors.groupingBy(LegDto::aircraftId, LinkedHashMap::new, Collectors.toList()));

        Map<String, VigilAlert> existing = alertRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(VigilAlert::getSignature, Function.identity(), (a, b) -> a));
        Set<String> seen = new HashSet<>();
        List<VigilAlert> touched = new ArrayList<>();
        List<VigilFlightDto> flights = new ArrayList<>(legs.size());

        for (LegDto leg : legs) {
            Context ctx = new Context(leg, now, previousOf(byAircraft, leg),
                    services.get(leg.id()), permits.get(leg.id()), crew.get(leg.id()),
                    mel.getOrDefault(leg.aircraftId(), List.of()));
            List<Hit> hits = evaluate(ctx);
            Risk risk = assess(hits);
            flights.add(ctx.toDto(risk));
            for (Hit hit : hits) {
                VigilAlert alert = upsert(tenantId, existing, ctx.signature(hit.rule), hit, ctx, risk.score, now);
                seen.add(alert.getSignature());
                touched.add(alert);
            }
        }

        VigilFleetDto fleetPicture = fleetPicture(fleet, legs, now);
        if ("HIGH".equals(fleetPicture.capacityLevel())) {
            Hit hit = new Hit("FLEET_CAPACITY", "Fleet", "Fleet capacity risk", VigilSeverity.HIGH, 0,
                    "Fleet capacity risk predicted within the next " + fleetPicture.horizonHours()
                            + " hours. Demand " + fleetPicture.demandSectors() + " sectors vs "
                            + fleetPicture.availableTails() + " available tails. "
                            + String.join("; ", fleetPicture.capacityRisks()),
                    "Schedule may not be coverable.",
                    "Review allocations and maintenance windows for the next "
                            + fleetPicture.horizonHours() + "h.");
            VigilAlert alert = upsert(tenantId, existing, "FLEET_CAPACITY|||" + today, hit, null, null, now);
            alert.setMethod("fleet");
            seen.add(alert.getSignature());
            touched.add(alert);
        }

        // ALERTS.reconcile (l. 98723) : ce qui n'a pas ete revu est resolu,
        // automatiquement, et le dit.
        for (VigilAlert alert : existing.values()) {
            if (alert.getStatus().active() && !seen.contains(alert.getSignature())) {
                alert.setStatus(VigilAlertStatus.RESOLVED);
                alert.setResolvedAt(now);
                alert.setResolvedBy(AUTO_RESOLVED);
                touched.add(alert);
            }
        }
        alertRepository.saveAll(touched);

        List<VigilAlertDto> active = alertRepository.findActive(tenantId).stream().map(this::toDto).toList();
        VigilCountsDto counts = counts(active);
        Snapshot snapshot = new Snapshot(now, flights, fleetPicture, counts, active);
        lastScan.put(tenantId, snapshot);

        return new VigilPanelDto(state(counts), counts, flights.size(), now, active, flights, fleetPicture);
    }

    @Override
    @Transactional
    public VigilAlertDto setStatus(UUID tenantId, UUID alertId, String status, UUID actorId) {
        VigilAlertStatus target;
        try {
            target = VigilAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("VIGIL_STATUS_UNKNOWN",
                    "Unknown VIGIL alert status: " + status);
        }
        VigilAlert alert = alertRepository.findByTenantIdAndId(tenantId, alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("VIGIL alert", alertId));
        alert.setStatus(target);
        alert.setUpdatedBy(actorId);
        if (target == VigilAlertStatus.RESOLVED || target == VigilAlertStatus.DISMISSED) {
            alert.setResolvedAt(OffsetDateTime.now(ZoneOffset.UTC));
            alert.setResolvedBy(actorId == null ? "occ" : actorId.toString());
        }
        return toDto(alertRepository.save(alert));
    }

    /* ═══════════════════════ RULES (l. 98327) ════════════════════════════ */

    private List<Hit> evaluate(Context ctx) {
        List<Hit> hits = new ArrayList<>();
        double h = ctx.hoursToDeparture();
        boolean inWindow = h >= -1 && h <= vigil.getHorizonHours();

        // AOG_FLIGHT — l'appareil affecte n'est pas en ligne.
        if (!ctx.done && inWindow && !"SERVICEABLE".equals(ctx.leg.aircraftStatus())) {
            boolean aog = "AOG".equals(ctx.leg.aircraftStatus());
            VigilSeverity sev = aog ? (h <= 3 ? VigilSeverity.CRITICAL : VigilSeverity.HIGH)
                    : (h <= 3 ? VigilSeverity.HIGH : VigilSeverity.WARNING);
            hits.add(new Hit("AOG_FLIGHT", "Aircraft", "Aircraft unavailable for scheduled flight", sev,
                    aog ? 30 : 18,
                    "Aircraft " + ctx.leg.registration() + " is " + (aog ? "AOG" : "in maintenance")
                            + " with " + ctx.label() + " scheduled at " + utc(ctx.leg.std()) + " UTC ("
                            + fmtH(h) + " from now).",
                    "Flight cannot operate on the assigned tail.",
                    "Review aircraft allocation — swap tail or revise the schedule. CAMO release required before dispatch."));
        }

        // AIRWORTHINESS — un item MEL interdit la mise en ligne.
        if (!ctx.done) {
            List<MelItemDto> blocking = ctx.mel.stream().filter(MelItemDto::blocksDispatch).toList();
            if (!blocking.isEmpty()) {
                String refs = blocking.stream().limit(3).map(MelItemDto::reference)
                        .collect(Collectors.joining("; "));
                hits.add(new Hit("AIRWORTHINESS", "Maintenance", "Airworthiness block on assigned aircraft",
                        VigilSeverity.CRITICAL, 28,
                        "CAMO derived state for " + ctx.leg.registration() + " is NOT AIRWORTHY — " + refs + ".",
                        "Dispatch on this tail is not permitted.",
                        "Open CAMO for " + ctx.leg.registration() + " and resolve the blocking item, or reassign the flight."));
            }
        }

        // PERMIT_PENDING — permis non confirmes pres du depart.
        if (!ctx.done && ctx.permits != null && ctx.permits.outstanding() > 0 && h >= 0 && h <= vigil.getHorizonHours()) {
            VigilSeverity sev = h <= vigil.getPermitCriticalHours() ? VigilSeverity.CRITICAL
                    : h <= vigil.getPermitWarnHours() ? VigilSeverity.HIGH : VigilSeverity.WARNING;
            int n = ctx.permits.outstanding();
            hits.add(new Hit("PERMIT_PENDING", "Permits", "Permit not confirmed near departure", sev,
                    sev == VigilSeverity.CRITICAL ? 25 : sev == VigilSeverity.HIGH ? 16 : 8,
                    "Permit" + (n > 1 ? "s" : "") + " pending (" + n + " of " + ctx.permits.total()
                            + " not confirmed) with STD in " + fmtH(h) + ".",
                    "Departure clearance at risk; possible delay or reroute.",
                    "Contact the permit provider / CAA and update the permit status in the flight label."));
        }

        // SVC_UNCONFIRMED — services demandes, non confirmes pres du depart.
        if (!ctx.done && ctx.services != null && ctx.services.total() > ctx.services.confirmed()
                && h >= 0 && h <= vigil.getHorizonHours()) {
            VigilSeverity sev = h <= vigil.getServiceCriticalHours() ? VigilSeverity.CRITICAL
                    : h <= vigil.getServiceWarnHours() ? VigilSeverity.HIGH : VigilSeverity.WARNING;
            int open = ctx.services.total() - ctx.services.confirmed();
            hits.add(new Hit("SVC_UNCONFIRMED", "Services", "Requested service not confirmed near departure", sev,
                    sev == VigilSeverity.CRITICAL ? 18 : sev == VigilSeverity.HIGH ? 12 : 6,
                    open + " service(s) requested but unconfirmed (" + ctx.services.confirmed() + " of "
                            + ctx.services.total() + " confirmed) with STD in " + fmtH(h) + ".",
                    "Ground operation may not be ready at STD.",
                    "Chase the supplier(s) and record confirmation in the Services tab."));
        }

        // CREW_INCOMPLETE — sieges non armes.
        if (!ctx.done && ctx.crew != null && h >= 0 && h <= vigil.getHorizonHours()
                && ctx.crew.seatsFilled() < ctx.crew.minimumSeats()) {
            VigilSeverity sev = h <= vigil.getCrewCriticalHours() ? VigilSeverity.CRITICAL
                    : h <= vigil.getCrewWarnHours() ? VigilSeverity.HIGH : VigilSeverity.WARNING;
            hits.add(new Hit("CREW_INCOMPLETE", "Crew", "Crew incomplete", sev,
                    sev == VigilSeverity.CRITICAL ? 22 : sev == VigilSeverity.HIGH ? 14 : 7,
                    "Crew " + ctx.crew.seatsFilled() + "/" + ctx.crew.minimumSeats() + " assigned for "
                            + ctx.label() + " (STD in " + fmtH(h) + ").",
                    "Flight cannot be released without a complete legal crew.",
                    "Assign crew from the Crew tab (only legal candidates are offered)."));
        }

        // CREW_FTL — legalite de l'equipage affecte.
        if (!ctx.done && ctx.crew != null && !ctx.crew.members().isEmpty()) {
            boolean breach = "BREACH".equals(ctx.crew.ftlStatus()) || "EXPIRED".equals(ctx.crew.documentStatus());
            boolean warning = "WARNING".equals(ctx.crew.ftlStatus()) || "EXPIRING".equals(ctx.crew.documentStatus());
            if (breach || warning) {
                String issue = breach
                        ? ("EXPIRED".equals(ctx.crew.documentStatus())
                        ? "A licence, medical or recurrent training is expired on the day of the flight."
                        : "An assignment is outside the flight-time limitations (ORO.FTL).")
                        : ("WARNING".equals(ctx.crew.ftlStatus())
                        ? "An assignment relies on a reduced rest or has no FTL margin."
                        : "A crew document expires within thirty days.");
                hits.add(new Hit("CREW_FTL", "Crew", "Crew legality / FTL risk",
                        breach ? VigilSeverity.CRITICAL : VigilSeverity.WARNING, breach ? 20 : 8,
                        issue,
                        "Assignment may be illegal under ORO.FTL.",
                        "Open the crew panel and resolve the flagged item(s)."));
            }
        }

        // TURNAROUND — escale sous le minimum de l'exploitant.
        if (!ctx.done && ctx.previous != null) {
            Duration turn = Duration.between(effectiveArrival(ctx.previous), ctx.etd);
            Duration minimum = ops.getMinimumTurnaround();
            if (turn.compareTo(minimum) < 0) {
                boolean tight = turn.compareTo(Duration.ofMinutes(25)) < 0;
                hits.add(new Hit("TURNAROUND", "Timeline", "Turnaround below minimum",
                        tight ? VigilSeverity.HIGH : VigilSeverity.WARNING, tight ? 10 : 5,
                        "Turnaround before " + ctx.label() + " is " + fmtH(Math.max(0, turn.toMinutes()) / 60.0)
                                + " (minimum " + fmtH(minimum.toMinutes() / 60.0) + ").",
                        "Knock-on delay likely to propagate to this sector.",
                        "Anticipate a delay revision or adjust the previous sector."));
            }
        }

        // OVERDUE_STATUS — heure d'arrivee passee, statut jamais avance.
        if (!ctx.done && ctx.leg.inAt() == null && ctx.eta.plusMinutes(6).isBefore(ctx.now)) {
            hits.add(new Hit("OVERDUE_STATUS", "Data Quality", "Flight past ETA without status update",
                    VigilSeverity.WARNING, 4,
                    ctx.label() + " shows \"" + ctx.leg.status() + "\" but its expected arrival ("
                            + utc(ctx.eta) + ") has passed.",
                    "Downstream modules (handover, reports, FTL post-flight check) are working on stale data.",
                    "Set ATA / close the flight, or update the delay."));
        }

        // SLOT_CONFLICT — CTOT et ETD desaccordes de plus de quinze minutes.
        if (!ctx.done && ctx.leg.ctot() != null
                && Math.abs(Duration.between(ctx.leg.ctot(), ctx.etd).toMinutes()) > 15) {
            hits.add(new Hit("SLOT_CONFLICT", "Timeline", "ATC slot vs ETD mismatch", VigilSeverity.WARNING, 5,
                    "CTOT " + utc(ctx.leg.ctot()) + " differs from current ETD " + utc(ctx.etd)
                            + " by more than 15 min.",
                    "Missed slot or unnecessary holding.",
                    "Re-align the schedule with the slot, or request a new CTOT."));
        }
        return hits;
    }

    /* ═══════════════════════ RISK (l. 98665) ═════════════════════════════ */

    private Risk assess(List<Hit> hits) {
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        int total = 0;
        for (Hit hit : hits) {
            byCategory.merge(hit.category, hit.weight, Integer::sum);
            total += hit.weight;
        }
        // Sans moteur ML, la part « prediction » (jusqu'a 30 points chez
        // l'annexe) est zero — et le dire vaut mieux que l'inventer.
        int score = (int) Math.max(0, Math.min(100, Math.round(total * 2.2)));
        String level = score >= 75 ? "CRITICAL" : score >= 50 ? "HIGH" : score >= 25 ? "MODERATE" : "LOW";
        int sum = Math.max(1, byCategory.values().stream().mapToInt(Integer::intValue).sum());
        List<VigilContributorDto> contributors = new ArrayList<>();
        for (String category : RISK_CATEGORIES) {
            Integer points = byCategory.get(category);
            if (points != null) {
                contributors.add(new VigilContributorDto(category, points, Math.round(100f * points / sum)));
            }
        }
        for (Map.Entry<String, Integer> other : byCategory.entrySet()) {
            if (!RISK_CATEGORIES.contains(other.getKey())) {
                contributors.add(new VigilContributorDto(other.getKey(), other.getValue(),
                        Math.round(100f * other.getValue() / sum)));
            }
        }
        contributors.sort(Comparator.comparingInt(VigilContributorDto::points).reversed());
        List<String> facts = hits.stream().map(hit -> hit.why).toList();
        List<String> recommendations = new ArrayList<>(new LinkedHashSet<>(hits.stream().map(hit -> hit.action).toList()));
        return new Risk(score, level, contributors, facts, recommendations);
    }

    /* ═══════════════════════ ALERTS (l. 98691) ═══════════════════════════ */

    private VigilAlert upsert(UUID tenantId, Map<String, VigilAlert> existing, String signature,
                              Hit hit, Context ctx, Integer risk, OffsetDateTime now) {
        VigilAlert alert = existing.get(signature);
        if (alert == null) {
            alert = new VigilAlert();
            alert.setTenantId(tenantId);
            alert.setSignature(signature);
            alert.setSource(Source.engine("VIGIL", "1.0.0"));
            alert.setFlightDate(ctx == null ? now.toLocalDate() : ctx.leg.std().atZoneSameInstant(ZoneOffset.UTC).toLocalDate());
            alert.setLegId(ctx == null ? null : ctx.leg.id());
            alert.setFlightNo(ctx == null ? null : ctx.leg.flightNo());
            alert.setRegistration(ctx == null ? null : ctx.leg.registration());
            alert.setStatus(VigilAlertStatus.OPEN);
            existing.put(signature, alert);
        } else if (alert.getStatus() == VigilAlertStatus.RESOLVED) {
            // La condition est revenue : l'alerte rouvre, elle ne se duplique pas.
            alert.setStatus(VigilAlertStatus.OPEN);
            alert.setReopenedAt(now);
            alert.setResolvedAt(null);
            alert.setResolvedBy(null);
        }
        alert.setRule(hit.rule);
        alert.setName(hit.name);
        alert.setCategory(hit.category);
        alert.setSeverity(hit.severity);
        alert.setWhy(hit.why);
        alert.setImpact(hit.impact);
        alert.setAction(hit.action);
        alert.setRisk(risk);
        alert.setLastSeenAt(now);
        return alert;
    }

    private VigilCountsDto counts(List<VigilAlertDto> active) {
        int critical = 0;
        int high = 0;
        int warning = 0;
        int info = 0;
        for (VigilAlertDto alert : active) {
            switch (alert.severity()) {
                case "CRITICAL" -> critical++;
                case "HIGH" -> high++;
                case "WARNING" -> warning++;
                default -> info++;
            }
        }
        return new VigilCountsDto(critical, high, warning, info);
    }

    private String state(VigilCountsDto counts) {
        return counts.critical() > 0 ? "CRITICAL" : counts.high() > 0 ? "WARNING" : "ACTIVE";
    }

    /* ═══════════════════════ FLEET (l. 98566) ════════════════════════════ */

    private VigilFleetDto fleetPicture(List<AircraftDto> fleet, List<LegDto> legs, OffsetDateTime now) {
        Map<UUID, List<LegDto>> byAircraft = legs.stream()
                .filter(leg -> !"CANCELLED".equals(leg.status()))
                .collect(Collectors.groupingBy(LegDto::aircraftId));

        List<String> aog = new ArrayList<>();
        List<String> maintenance = new ArrayList<>();
        List<String> idle = new ArrayList<>();
        Map<String, double[]> byType = new TreeMap<>(); // type -> [tails, blockHours, capacityHours]
        int supply = 0;
        int demand = 0;
        List<String> risks = new ArrayList<>();
        int horizon = vigil.getCapacityLookaheadHours();
        OffsetDateTime horizonEnd = now.plusHours(horizon);

        for (AircraftDto tail : fleet) {
            List<LegDto> mine = byAircraft.getOrDefault(tail.id(), List.of());
            boolean serviceable = "SERVICEABLE".equals(tail.status());
            if ("AOG".equals(tail.status())) {
                aog.add(tail.registration());
            } else if ("MAINTENANCE".equals(tail.status())) {
                maintenance.add(tail.registration());
            } else if (mine.isEmpty()) {
                idle.add(tail.registration());
            }
            double block = mine.stream()
                    .mapToDouble(leg -> Duration.between(leg.std(), leg.sta()).toMinutes() / 60.0)
                    .sum();
            String type = tail.icaoType() == null ? "—" : tail.icaoType();
            double[] acc = byType.computeIfAbsent(type, key -> new double[3]);
            acc[0]++;
            acc[1] += block;
            // Dix heures productives par appareil disponible et par jour :
            // l'hypothese documentee de l'annexe (l. 98575), la meme que la
            // reference d'utilisation de la Timeline.
            acc[2] += serviceable ? ops.getDailyBlockHourReference() : 0;

            long ahead = mine.stream()
                    .filter(leg -> !effectiveDeparture(leg).isBefore(now) && effectiveDeparture(leg).isBefore(horizonEnd))
                    .count();
            demand += (int) ahead;
            if (serviceable) {
                supply++;
            } else if (ahead > 0) {
                risks.add(tail.registration() + " is " + tail.status() + " with " + ahead
                        + " sector(s) inside the next " + horizon + "h");
            }
        }

        List<VigilUtilisationDto> utilisation = byType.entrySet().stream()
                .map(entry -> new VigilUtilisationDto(entry.getKey(), (int) entry.getValue()[0],
                        Math.round(entry.getValue()[1] * 10) / 10.0, (int) entry.getValue()[2],
                        entry.getValue()[2] > 0 ? (int) Math.round(100 * entry.getValue()[1] / entry.getValue()[2]) : 0))
                .toList();

        double ratio = supply > 0 ? demand / (supply * 3.0) : 1;
        String level = ratio > 1 ? "HIGH" : ratio > 0.75 ? "MODERATE" : "LOW";
        return new VigilFleetDto(fleet.size(), aog, maintenance, idle, demand, supply, horizon, level, risks, utilisation);
    }

    /* ═══════════════════════ AGENT (l. 98936) ════════════════════════════ */

    @Override
    public VigilAnswerDto ask(UUID tenantId, String question) {
        Snapshot snapshot = lastScan.get(tenantId);
        if (snapshot == null) {
            panel(tenantId);
            snapshot = lastScan.get(tenantId);
        }
        String q = question == null ? "" : question.trim();
        String lower = q.toLowerCase(Locale.ROOT);
        Matcher flight = Pattern.compile("\\b([a-z]{2,3}\\s?\\d{2,4})\\b").matcher(lower);
        String answer;
        if (flight.find() && lower.matches(".*(analy|risk|risque|pourquoi|why|check|status|statut).*")) {
            answer = answerFlight(snapshot, flight.group(1).toUpperCase(Locale.ROOT).replace(" ", ""));
        } else if (lower.matches(".*(fleet|flotte).*") && lower.contains("optimi")) {
            answer = "VIGIL: fleet optimisation (ferry audit, co-located availability, load balance) reads tail "
                    + "positions through the day. Positions are not computed server-side yet — no opportunity "
                    + "is claimed. FACT: " + snapshot.fleet.idle().size() + " idle tail(s) today"
                    + (snapshot.fleet.idle().isEmpty() ? "." : ": " + String.join(", ", snapshot.fleet.idle()) + ".");
        } else if (lower.matches(".*(fleet|flotte).*")) {
            answer = answerFleet(snapshot);
        } else if (lower.matches(".*(handover|passation|shift).*")) {
            answer = "VIGIL: no shift handover note is on file server-side — the annexe compared a note kept in "
                    + "the browser. Nothing to compare yet.";
        } else if (lower.matches(".*(report|rapport).*")) {
            answer = "VIGIL: the 6-hourly report engine is not ported — there is no report to show. "
                    + "Current picture: " + snapshot.flights.size() + " flights monitored · "
                    + snapshot.counts.critical() + " critical · " + snapshot.counts.high() + " high · "
                    + snapshot.counts.warning() + " warnings.";
        } else if (lower.matches(".*\\b(\\d{1,2})\\s*(h|hour|heures?)\\b.*") && lower.matches(".*(next|prochain|risk|risque).*")) {
            Matcher hours = Pattern.compile("(\\d{1,2})\\s*(h|hour|heures?)").matcher(lower);
            hours.find();
            answer = answerHorizon(snapshot, Integer.parseInt(hours.group(1)));
        } else if (lower.matches(".*(risk|risque|at risk).*")) {
            answer = answerRisks(snapshot);
        } else if (flight.reset().find()) {
            answer = answerFlight(snapshot, flight.group(1).toUpperCase(Locale.ROOT).replace(" ", ""));
        } else {
            answer = answerSituation(snapshot);
        }
        return new VigilAnswerDto(answer, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String answerSituation(Snapshot s) {
        List<VigilFlightDto> worst = s.flights.stream()
                .sorted(Comparator.comparingInt(VigilFlightDto::riskScore).reversed()).limit(3).toList();
        StringBuilder out = new StringBuilder();
        out.append("CURRENT SITUATION — ").append(HHMM.format(s.at)).append(" UTC\n");
        out.append("FACT: ").append(s.flights.size()).append(" flights monitored · ")
                .append(s.counts.critical()).append(" critical · ").append(s.counts.high()).append(" high · ")
                .append(s.counts.warning()).append(" warnings. Fleet capacity pressure: ")
                .append(s.fleet.capacityLevel()).append(".\n");
        if (worst.isEmpty() || worst.get(0).riskScore() < 25) {
            out.append("No flight currently above LOW risk.\n");
        } else {
            out.append("Highest-risk flights: ").append(worst.stream()
                    .filter(f -> f.riskScore() >= 25)
                    .map(f -> f.flightNo() + " (" + f.riskScore() + "/" + f.riskLevel() + ")")
                    .collect(Collectors.joining(", "))).append(".\n");
        }
        out.append("PREDICTION: no delay model is trained server-side — no probability is claimed.\n");
        String action = s.alerts.isEmpty() ? "Continue normal monitoring." : s.alerts.get(0).action();
        out.append("RECOMMENDATION: ").append(action == null ? "Continue normal monitoring." : action);
        return out.toString();
    }

    private String answerFlight(Snapshot s, String flightNo) {
        VigilFlightDto f = s.flights.stream()
                .filter(x -> x.flightNo() != null && x.flightNo().replace(" ", "").equalsIgnoreCase(flightNo))
                .findFirst().orElse(null);
        if (f == null) {
            return "VIGIL: flight " + flightNo + " not found in today's operational data.";
        }
        StringBuilder out = new StringBuilder();
        out.append("VIGIL ANALYSIS — ").append(f.flightNo()).append(" (").append(f.registration()).append(" · ")
                .append(f.depIcao()).append("→").append(f.arrIcao()).append(" · STD ").append(HHMM.format(f.std())).append("Z)\n");
        out.append("RISK: ").append(f.riskScore()).append("/100 — ").append(f.riskLevel()).append("\n");
        if (!f.contributors().isEmpty()) {
            out.append("Contributors: ").append(f.contributors().stream()
                    .map(c -> c.category() + " " + c.share() + "%").collect(Collectors.joining(" · "))).append("\n");
        }
        out.append("FACTS:\n");
        if (f.facts().isEmpty()) {
            out.append("  • No rule currently firing on this flight.\n");
        } else {
            f.facts().forEach(fact -> out.append("  • ").append(fact).append("\n"));
        }
        out.append("PREDICTION: no delay model is trained server-side — no probability is claimed.\n");
        out.append("RECOMMENDATION:\n");
        if (f.recommendations().isEmpty()) {
            out.append("  • No action required.");
        } else {
            out.append(f.recommendations().stream().map(r -> "  • " + r).collect(Collectors.joining("\n")));
        }
        return out.toString();
    }

    private String answerRisks(Snapshot s) {
        List<VigilFlightDto> list = s.flights.stream().filter(f -> f.riskScore() >= 25)
                .sorted(Comparator.comparingInt(VigilFlightDto::riskScore).reversed()).toList();
        if (list.isEmpty()) {
            return "VIGIL: no flight is currently above LOW risk."
                    + (s.counts.warning() > 0 ? " " + s.counts.warning() + " low-grade warnings are open." : "");
        }
        return "FLIGHTS AT RISK (" + list.size() + "):\n" + list.stream().limit(8)
                .map(f -> "  • " + f.flightNo() + " (" + f.registration() + ") — " + f.riskScore() + "/100 "
                        + f.riskLevel() + " — top factor: "
                        + (f.contributors().isEmpty() ? "—" : f.contributors().get(0).category()))
                .collect(Collectors.joining("\n"));
    }

    private String answerHorizon(Snapshot s, int hours) {
        OffsetDateTime end = s.at.plusHours(hours);
        List<VigilFlightDto> list = s.flights.stream()
                .filter(f -> !f.etd().isBefore(s.at) && !f.etd().isAfter(end)).toList();
        List<VigilFlightDto> risky = list.stream().filter(f -> f.riskScore() >= 25)
                .sorted(Comparator.comparingInt(VigilFlightDto::riskScore).reversed()).toList();
        StringBuilder out = new StringBuilder();
        out.append("NEXT ").append(hours).append("H — ").append(list.size()).append(" departures monitored.\n");
        out.append("FACT: ").append(risky.size()).append(" of them carry MODERATE+ risk.\n");
        risky.stream().limit(6).forEach(f -> out.append("  • ").append(f.flightNo()).append(" T-")
                .append(fmtH(Duration.between(s.at, f.etd()).toMinutes() / 60.0)).append(" — ")
                .append(f.riskScore()).append("/100 (")
                .append(f.contributors().isEmpty() ? "—" : f.contributors().get(0).category()).append(")\n"));
        out.append("RECOMMENDATION: work the list top-down; VIGIL re-evaluates every ")
                .append(vigil.getScanEverySeconds()).append("s.");
        return out.toString();
    }

    private String answerFleet(Snapshot s) {
        VigilFleetDto f = s.fleet;
        StringBuilder out = new StringBuilder();
        out.append("FLEET INTELLIGENCE — ").append(HHMM.format(s.at)).append(" UTC\n");
        out.append("FACT: ").append(f.tails()).append(" tails · AOG: ").append(f.aog().isEmpty() ? "none" : String.join(", ", f.aog()))
                .append(" · maintenance: ").append(f.maintenance().isEmpty() ? "none" : String.join(", ", f.maintenance()))
                .append(" · idle today: ").append(f.idle().isEmpty() ? "none" : String.join(", ", f.idle())).append(".\n");
        f.utilisation().forEach(u -> out.append("  • ").append(u.fleet()).append(": ").append(u.blockHours())
                .append("h block (").append(u.percentOfCapacity()).append("% of capacity)\n"));
        out.append("PREDICTION: capacity pressure ").append(f.capacityLevel()).append(" over the next ")
                .append(f.horizonHours()).append("h (").append(f.demandSectors()).append(" sectors / ")
                .append(f.availableTails()).append(" available tails).\n");
        out.append("RECOMMENDATION: ").append(f.capacityRisks().isEmpty()
                ? "No optimisation opportunity detected right now." : String.join("; ", f.capacityRisks()) + ".");
        return out.toString();
    }

    /* ═══════════════════════ helpers ═════════════════════════════════════ */

    private LegDto previousOf(Map<UUID, List<LegDto>> byAircraft, LegDto leg) {
        List<LegDto> mine = byAircraft.getOrDefault(leg.aircraftId(), List.of());
        LegDto previous = null;
        for (LegDto other : mine) {
            if (other.id().equals(leg.id())) {
                return previous;
            }
            if (!"CANCELLED".equals(other.status())) {
                previous = other;
            }
        }
        return null;
    }

    private static OffsetDateTime effectiveDeparture(LegDto leg) {
        return leg.outAt() != null ? leg.outAt() : leg.etd() != null ? leg.etd() : leg.std();
    }

    private static OffsetDateTime effectiveArrival(LegDto leg) {
        if (leg.inAt() != null) {
            return leg.inAt();
        }
        if (leg.outAt() != null) {
            return leg.outAt().plus(Duration.between(leg.std(), leg.sta()));
        }
        return leg.eta() != null ? leg.eta() : leg.sta();
    }

    private VigilAlertDto toDto(VigilAlert a) {
        return new VigilAlertDto(a.getId(), a.getSignature(), a.getRule(), a.getName(), a.getCategory(),
                a.getLegId(), a.getFlightNo(), a.getRegistration(), a.getSeverity().name(), a.getRisk(),
                a.getMethod(), a.getWhy(), a.getImpact(), a.getAction(), a.getStatus().name().replace('_', ' '),
                a.getCreatedAt(), a.getLastSeenAt());
    }

    private static String utc(OffsetDateTime when) {
        return when == null ? "—" : HHMM.format(when.atZoneSameInstant(ZoneOffset.UTC));
    }

    /** {@code fmtH()} de l'annexe : 1h05, -0h20. */
    private static String fmtH(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours)) {
            return "—";
        }
        boolean negative = hours < 0;
        double abs = Math.abs(hours);
        int hh = (int) Math.floor(abs);
        int mm = (int) Math.round((abs - hh) * 60);
        if (mm == 60) {
            hh++;
            mm = 0;
        }
        return (negative ? "-" : "") + hh + "h" + String.format("%02d", mm);
    }

    private record Hit(String rule, String category, String name, VigilSeverity severity, int weight,
                       String why, String impact, String action) {
    }

    private record Risk(int score, String level, List<VigilContributorDto> contributors,
                        List<String> facts, List<String> recommendations) {
    }

    private record Snapshot(OffsetDateTime at, List<VigilFlightDto> flights, VigilFleetDto fleet,
                            VigilCountsDto counts, List<VigilAlertDto> alerts) {
    }

    /** {@code buildCtx()} de l'annexe (l. 98753) — une etape et ce qu'on sait d'elle. */
    private static final class Context {
        final LegDto leg;
        final OffsetDateTime now;
        final LegDto previous;
        final LegServicesSummary services;
        final LegPermitsSummary permits;
        final LegCrewDto crew;
        final List<MelItemDto> mel;
        final OffsetDateTime etd;
        final OffsetDateTime eta;
        final boolean done;

        Context(LegDto leg, OffsetDateTime now, LegDto previous, LegServicesSummary services,
                LegPermitsSummary permits, LegCrewDto crew, List<MelItemDto> mel) {
            this.leg = leg;
            this.now = now;
            this.previous = previous;
            this.services = services;
            this.permits = permits;
            this.crew = crew;
            this.mel = mel;
            this.etd = effectiveDeparture(leg);
            this.eta = effectiveArrival(leg);
            this.done = leg.inAt() != null || "ARRIVED".equals(leg.status())
                    || "CLOSED".equals(leg.status()) || "CANCELLED".equals(leg.status());
        }

        double hoursToDeparture() {
            return Duration.between(now, etd).toMinutes() / 60.0;
        }

        String label() {
            return leg.flightNo() != null && !leg.flightNo().isBlank() ? leg.flightNo()
                    : leg.depIcao() + "–" + leg.arrIcao();
        }

        /** {@code ALERTS.sig()} : regle | immatriculation | vol | date. */
        String signature(String rule) {
            return rule + "|" + leg.registration() + "|" + label() + "|"
                    + leg.std().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        }

        VigilFlightDto toDto(Risk risk) {
            return new VigilFlightDto(leg.id(), label(), leg.registration(), leg.depIcao(), leg.arrIcao(),
                    leg.std(), etd, eta, leg.status(), risk.score, risk.level, risk.contributors,
                    risk.facts, risk.recommendations);
        }
    }
}
