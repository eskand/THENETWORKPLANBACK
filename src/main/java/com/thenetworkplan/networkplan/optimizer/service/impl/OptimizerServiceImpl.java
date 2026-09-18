package com.thenetworkplan.networkplan.optimizer.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.FlightType;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.CostParamsDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptActionDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptAnomalyDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptFollowUpDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptOperationDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizationResultDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizeCommand;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizerSetupDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.PlanCostDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.ScopeDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.SourceDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.TypeRateDto;
import com.thenetworkplan.networkplan.optimizer.service.OptimizerService;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.sim.domain.AnomalyType;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le Timeline Optimizer, cote serveur.
 *
 * <p>La structure est celle du Simulation Center de l'annexe, bloc par bloc :
 * ADAPTER ({@code LiveAdapter.load}, l. 93021) lit le plan ; DETECTOR
 * ({@code Detector.scan}, l. 93212) leve les inefficiences ; COST
 * ({@code Cost}, l. 90679) les valorise ; le SCORE ({@code scorePlan},
 * l. 90571) note le plan ; l'ENGINE produit une action par inefficience ;
 * {@code deriveOps} (l. 93592) transforme le plan reoptimise en instructions
 * atomiques ; {@code deriveFollowUps} (l. 93823) liste ce qui se fait dans un
 * autre module. Les libelles sont les siens.
 *
 * <p><b>Ce qui n'est pas repris, a dessein.</b> {@code solveRate = 0.6 +
 * Math.random()*0.25} et les {@code rint()} des gains : ici chaque
 * inefficience dans le perimetre recoit l'action de son bareme, et un gain
 * n'est ecrit que lorsqu'il se calcule sur le plan. Ce que le serveur ne sait
 * pas encore — un vivier d'equipage de remplacement, les positions
 * intermediaires des appareils — rend une instruction « manual », jamais un
 * chiffre.
 */
@Service
@Transactional(readOnly = true)
public class OptimizerServiceImpl implements OptimizerService {

    private static final double CRUISE_KTS = 440;
    private static final double TAXI_H = 0.35;
    private static final String DEFAULT_BASE = "DTTA";

    /** {@code OPT_SCOPE} (l. 89819) — l'ordre est celui des cases a cocher. */
    private static final List<ScopeDto> SCOPES = List.of(
            new ScopeDto("FERRY", "Ferries & repositioning", List.of("FERRY_UNNECESSARY", "REPOSITIONING_UNNECESSARY",
                    "REPOSITIONING_AFTER_CXL", "MISPOSITIONED", "FAR_FROM_DEMAND", "SAME_APT_CLUSTER")),
            new ScopeDto("CREW", "Crew (FTL / roster)", List.of("CREW_CONFLICT", "UNDERUTILIZED_CREW", "OVERUTILIZED_CREW", "CREW_REPOSITIONING")),
            new ScopeDto("DELAY", "Delays & sequencing", List.of("DELAY")),
            new ScopeDto("RECOV", "Cancellations / AOG recovery", List.of("CANCELLATION", "AOG", "AIRPORT_CLOSURE")),
            new ScopeDto("ROUTE", "Routing & rotations", List.of("ROTATION_INEFFICIENT")),
            new ScopeDto("FLEET", "Fleet balance & utilization", List.of("UNDERUTILIZED_AC", "OVERUTILIZED_AC", "DORMANT_AC")),
            new ScopeDto("MX", "Maintenance & CAMO", List.of("MX_CRITICAL", "CAMO_CONFLICT")),
            new ScopeDto("DISPATCH", "Dispatch readiness", List.of("DISPATCH_NOT_READY")),
            new ScopeDto("DEMAND", "Demand, VIP & empty legs", List.of("VIP_REQUEST", "COMBINABLE_FLIGHTS", "EMPTY_LEG_OPPORTUNITY", "ADDED_FLIGHT")),
            new ScopeDto("CONSTR", "Constraints (WX / APT / parking)", List.of("WX_IMPACT", "RWY_CLOSURE", "PARKING_LIMIT")));
    private static final Map<String, String> SCOPE_OF_TYPE = new HashMap<>();

    static {
        for (ScopeDto scope : SCOPES) {
            for (String type : scope.types()) {
                SCOPE_OF_TYPE.put(type, scope.key());
            }
        }
    }

    /** {@code COST_TYPES} (l. 90653) — la cle est cherchee dans le modele de l'appareil. */
    private static final List<TypeRateDto> TYPE_RATES = List.of(
            new TypeRateDto("LINEAGE", "Lineage 1000", 480, 1350, 720, 340, 2100, 1.10),
            new TypeRateDto("7X", "Falcon 7X", 320, 1100, 560, 300, 1750, 0.95),
            new TypeRateDto("900", "Falcon 900LX", 290, 950, 480, 280, 1550, 0.90),
            new TypeRateDto("2000LXS", "Falcon 2000LXS", 250, 830, 410, 260, 1400, 0.85),
            new TypeRateDto("2000", "Falcon 2000LX", 260, 850, 420, 260, 1400, 0.85),
            new TypeRateDto("LEGACY 650", "Legacy 650", 350, 1010, 540, 295, 1650, 0.94),
            new TypeRateDto("LEGACY", "Legacy 600", 340, 980, 520, 290, 1600, 0.92),
            new TypeRateDto("CJ4", "Citation CJ4", 195, 520, 280, 210, 900, 0.65),
            new TypeRateDto("CJ3", "Citation CJ3+", 175, 470, 250, 200, 850, 0.62),
            new TypeRateDto("CJ2", "Citation CJ2+", 160, 430, 220, 190, 800, 0.60),
            new TypeRateDto("CJ1", "Citation CJ1", 145, 400, 200, 180, 750, 0.55),
            new TypeRateDto("M2", "Citation M2", 130, 380, 190, 180, 700, 0.55));

    /** {@code COST_GLOBALS} (l. 90668). */
    private static final CostParamsDto DEFAULT_COSTS =
            new CostParamsDto(6.80, 45, 4500, 22000, 3200, 1400, 650, 6500, 9000, "USD", "$", "TUN");

    private static final Map<String, Integer> SEVERITY_WEIGHT = Map.of("crit", 4, "high", 3, "med", 2, "low", 1);

    private final LegRepository legRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftService aircraftService;
    private final CrewAssignmentService crewAssignmentService;
    private final PermitService permitService;
    private final GroundServiceService groundServiceService;
    private final AirportService airportService;
    private final OpsProperties ops;

    public OptimizerServiceImpl(LegRepository legRepository, AircraftRepository aircraftRepository,
                                AircraftService aircraftService, CrewAssignmentService crewAssignmentService,
                                PermitService permitService, GroundServiceService groundServiceService,
                                AirportService airportService, OpsProperties ops) {
        this.legRepository = legRepository;
        this.aircraftRepository = aircraftRepository;
        this.aircraftService = aircraftService;
        this.crewAssignmentService = crewAssignmentService;
        this.permitService = permitService;
        this.groundServiceService = groundServiceService;
        this.airportService = airportService;
        this.ops = ops;
    }

    /* ═══════════════════ SETUP — LiveAdapter.sources() (l. 92995) ═══════════ */

    @Override
    public OptimizerSetupDto setup(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime start = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Leg> legs = legRepository.findProgramme(tenantId, start, start.plusDays(3));
        List<UUID> legIds = legs.stream().map(Leg::getId).toList();
        Map<UUID, LegCrewDto> crew = crewAssignmentService.findByLegIds(tenantId, legIds, today);
        long crewed = crew.values().stream().filter(c -> !c.members().isEmpty()).count();
        Map<UUID, List<MelItemDto>> mel = aircraftService.findOpenMelByAircraft(tenantId);
        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        Set<String> stations = new HashSet<>();
        legs.forEach(leg -> {
            stations.add(leg.getDepIcao());
            stations.add(leg.getArrIcao());
        });
        Map<String, AirportDto> airports = stations.isEmpty() ? Map.of() : airportService.findAllByIcao(stations);
        long withTrip = legs.stream().filter(leg -> leg.getTrip() != null).count();
        long withRisk = legs.stream().filter(leg -> leg.getRiskLevel() != null).count();

        List<SourceDto> sources = List.of(
                new SourceDto("TIMELINE", "Flight Timeline / flight source", true,
                        "ops.legs — " + legs.size() + " leg(s) over the next 3 days"),
                new SourceDto("DISPATCH", "Dispatch (readiness & launched flights)", true,
                        "service and permit requests, crew seats · " + legs.size() + " leg(s) in the programme"),
                new SourceDto("FF", "Flight Following (live status)", true, "leg status / ATD / ATA"),
                new SourceDto("CREW", "Crew — roster & FTL", crewed > 0,
                        crewed > 0 ? "assignments with ORO.FTL verdict on " + crewed + " leg(s)"
                                : "no crew assigned in this window"),
                new SourceDto("CAMO", "CAMO / Maintenance", true,
                        fleet.size() + " tails · " + mel.values().stream().mapToInt(List::size).sum() + " open MEL item(s)"),
                new SourceDto("APT", "Airport Data", !airports.isEmpty(),
                        airports.size() + " of " + stations.size() + " stations with coordinates, category and restrictions"),
                new SourceDto("SALES", "Sales / CRM (open demand)", withTrip > 0,
                        withTrip > 0 ? withTrip + " leg(s) attached to a dossier (trip)" : "no dossier attached"),
                new SourceDto("SMS", "Safety / SMS", withRisk > 0,
                        withRisk > 0 ? "risk level assessed on " + withRisk + " leg(s)" : "no risk assessment in this window"));
        return new OptimizerSetupDto(sources, SCOPES, DEFAULT_COSTS, TYPE_RATES, today);
    }

    /* ═══════════════════ RUN — SC.optRun() (l. 92439) ═══════════════════════ */

    @Override
    public OptimizationResultDto optimize(UUID tenantId, OptimizeCommand command) {
        long t0 = System.nanoTime();
        LocalDate startDate = command.startDate() != null ? command.startDate() : LocalDate.now(ZoneOffset.UTC);
        int days = Math.max(1, Math.min(14, command.days() == null ? 3 : command.days()));
        double fromH = command.fromHour() == null ? 0 : command.fromHour();
        double toH = command.toHour() == null ? 24 : command.toHour();
        if (toH <= fromH) {
            throw new BusinessRuleException("OPT_WINDOW_INVALID", "The \"To\" time must be after the \"From\" time");
        }
        List<String> scope = command.scope() == null || command.scope().isEmpty()
                ? SCOPES.stream().map(ScopeDto::key).toList() : command.scope();
        String objective = "COST".equalsIgnoreCase(command.objective()) ? "COST" : "SCORE";
        double target = command.target() == null ? 0 : Math.max(0, command.target());
        Cost cost = new Cost(command.costs() == null ? DEFAULT_COSTS : command.costs());

        Plan plan = load(tenantId, startDate, days, fromH, toH, cost);
        if (plan.flights.isEmpty()) {
            throw new BusinessRuleException("OPT_NO_FLIGHT", "No flight found in this date range / time window");
        }
        Scenario scn = new Scenario(plan, "Live optimization " + plan.days.get(0)
                + (plan.days.size() > 1 ? " → " + plan.days.get(plan.days.size() - 1) : "")
                + " " + hhmm(fromH) + "-" + hhmm(toH));
        detect(scn, cost);

        Set<String> scopeSet = new HashSet<>(scope);
        for (Anomaly a : scn.anomalies) {
            a.outOfScope = !scopeSet.contains(SCOPE_OF_TYPE.getOrDefault(a.type, "?"));
            a.estValue = cost.estValue(a, scn);
        }
        double score0 = scorePlan(scn, cost);
        PlanCost c0 = cost.planCost(scn);

        Engine engine = new Engine(scn, cost, objective, target);
        engine.run();
        Scenario after = engine.opt;
        double score1 = scorePlan(after, cost);
        PlanCost c1 = cost.planCost(after);

        for (Action x : engine.actions) {
            x.valueUsd = cost.actionValue(x, scn);
            x.severity = scn.byId(x.anomalyId) == null ? "med" : scn.byId(x.anomalyId).severity;
        }
        long saving = Math.round(c0.total - c1.total);
        List<Operation> operations = deriveOps(scn, after, engine.actions);
        int applicable = (int) operations.stream().filter(o -> o.applicable).count();
        int perOp = applicable > 0 ? (int) Math.round((double) saving / applicable) : 0;
        operations.forEach(o -> o.valueUsd = o.applicable ? perOp : 0);
        List<FollowUp> followUps = deriveFollowUps(engine.actions);
        List<String> steps = buildActionPlan(engine.actions);
        for (int i = 0; i < engine.actions.size(); i++) {
            engine.actions.get(i).step = i < steps.size() ? steps.get(i) : "";
        }

        long computeMs = Math.max(1, (System.nanoTime() - t0) / 1_000_000);
        double improvement = score0 > 0 ? round1((score1 - score0) / score0 * 100) : 0;
        return new OptimizationResultDto(scn.id, scn.name, startDate, days, fromH, toH,
                (int) plan.flights.stream().filter(f -> !"GROUND".equals(f.kind)).count(), plan.tails.size(),
                objective, target, scope, "COST".equals(objective) ? "monetary value" : "severity",
                engine.stopped, engine.detected, engine.corrected, engine.skipped,
                score0, score1, improvement,
                Math.round(c0.total), Math.round(c1.total), saving,
                c0.total > 0 ? round1(saving / c0.total * 100) : 0,
                Math.round(saving * 365.0 / Math.max(1, plan.days.size())),
                c0.toDto(), c1.toDto(), computeMs, cost.p.currency(), cost.p.symbol(),
                scn.exclusiveUseKept, List.copyOf(scn.exclusiveUseDetail),
                scn.anomalies.stream().map(this::toDto).toList(),
                engine.actions.stream().map(this::toDto).toList(),
                operations.stream().map(this::toDto).toList(),
                followUps.stream().map(f -> new OptFollowUpDto(f.id, f.module, f.brief, f.text)).toList(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /* ═══════════════════ ADAPTER — LiveAdapter.load() (l. 93021) ═══════════ */

    private Plan load(UUID tenantId, LocalDate startDate, int days, double fromH, double toH, Cost cost) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = start.plusDays(days);
        Plan plan = new Plan();
        plan.baseApt = cost.p.baseApt() == null || cost.p.baseApt().isBlank() ? DEFAULT_BASE : cost.p.baseApt().toUpperCase(Locale.ROOT);
        plan.window = new double[] {fromH, toH};
        for (int i = 0; i < days; i++) {
            plan.days.add(startDate.plusDays(i).toString());
        }

        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        for (Aircraft aircraft : fleet) {
            Tail tail = new Tail();
            tail.aircraftId = aircraft.getId();
            tail.reg = aircraft.getRegistration();
            tail.type = aircraft.getAircraftType() == null ? "" : aircraft.getAircraftType().getModel();
            tail.family = fleetFamily(tail.type);
            tail.baseStatus = aircraft.getStatus() == AircraftStatus.AOG ? "aog"
                    : aircraft.getStatus() == AircraftStatus.MAINTENANCE ? "maint" : "ok";
            tail.base = aircraft.getHomeBaseIcao() == null ? plan.baseApt : aircraft.getHomeBaseIcao();
            tail.nextCheckDueAt = aircraft.getNextCheckDueAt();
            tail.nextCheckLabel = aircraft.getNextCheckLabel();
            plan.tails.add(tail);
        }
        Map<UUID, Tail> tailById = plan.tails.stream().collect(Collectors.toMap(t -> t.aircraftId, t -> t));

        List<Leg> legs = legRepository.findProgramme(tenantId, start, end);
        List<UUID> legIds = legs.stream().map(Leg::getId).toList();
        Map<UUID, LegCrewDto> crewByLeg = crewAssignmentService.findByLegIds(tenantId, legIds, startDate);
        Map<UUID, LegServicesSummary> services = groundServiceService.summariseByLegIds(tenantId, legIds);
        Map<UUID, LegPermitsSummary> permits = permitService.summariseByLegIds(tenantId, legIds);
        Map<UUID, List<MelItemDto>> mel = aircraftService.findOpenMelByAircraft(tenantId);

        int fid = 0;
        for (Leg leg : legs) {
            Tail tail = tailById.get(leg.getAircraft().getId());
            if (tail == null) {
                continue;
            }
            OffsetDateTime dep = leg.getOutAt() != null ? leg.getOutAt() : leg.getEtd() != null ? leg.getEtd() : leg.getStd();
            OffsetDateTime arr = leg.getInAt() != null ? leg.getInAt() : leg.getEta() != null ? leg.getEta() : leg.getSta();
            LocalDate day = leg.getStd().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            int dayIdx = (int) Duration.between(start, day.atStartOfDay().atOffset(ZoneOffset.UTC)).toDays();
            double s = hours(dep, day);
            double e = hours(arr, day);
            // Seules les etapes qui touchent la bande horaire choisie (l. 93018).
            if (e <= fromH || s >= toH) {
                continue;
            }
            Flight f = new Flight();
            f.id = "F" + (++fid);
            f.legId = leg.getId();
            f.day = dayIdx;
            f.date = day.toString();
            f.reg = tail.reg;
            f.type = tail.type;
            f.fn = leg.getFlightNo() == null ? "—" : leg.getFlightNo();
            f.from = leg.getDepIcao();
            f.to = leg.getArrIcao();
            f.s = s;
            f.e = e;
            f.std = leg.getStd();
            f.sta = leg.getSta();
            f.status = leg.getStatus() == LegStatus.CANCELLED ? "cancelled"
                    : leg.getStatus() == LegStatus.DEPARTED ? "enroute" : "scheduled";
            f.kind = leg.getFlightType() == FlightType.MAINTENANCE ? "GROUND"
                    : (leg.getFlightType() == FlightType.FERRY || leg.getFlightType() == FlightType.POSITIONING) ? "FERRY" : "REV";
            f.pax = "REV".equals(f.kind) ? leg.getPaxCount() : 0;
            f.delayMin = (int) Math.max(0, Duration.between(leg.getStd(), dep).toMinutes());
            // Un dossier = un appareil, exclusivement : la cle de consolidation
            // est le trip, jamais le client ni le courtier (l. 89921).
            f.bookingRef = "REV".equals(f.kind) && leg.getTrip() != null ? "TRIP-" + leg.getTrip().getId().toString().substring(0, 8).toUpperCase(Locale.ROOT) : null;
            f.broker = leg.getTrip() == null ? null : leg.getTrip().getClientRef();
            f.paxGroup = f.bookingRef;
            LegCrewDto crew = crewByLeg.get(leg.getId());
            if (crew != null) {
                f.crew = crew.members().stream().map(CrewMemberDto::fullName).filter(n -> n != null).toList();
                f.crewFtl = crew.ftlStatus();
                f.crewDocs = crew.documentStatus();
                f.crewComplete = crew.complete();
                if (!crew.complete()) {
                    f.dispatchMissing.add("crew " + crew.seatsFilled() + "/" + crew.minimumSeats());
                }
            }
            LegPermitsSummary permit = permits.get(leg.getId());
            if (permit != null && permit.outstanding() > 0) {
                f.dispatchMissing.add(permit.outstanding() + " permit(s)");
            }
            LegServicesSummary service = services.get(leg.getId());
            if (service != null && service.total() > service.confirmed()) {
                f.dispatchMissing.add((service.total() - service.confirmed()) + " service(s)");
            }
            plan.flights.add(f);
            f.crew.forEach(plan.crews::add);
        }

        // Maintenance et CAMO : la prochaine visite dans la fenetre, et les MEL bloquants.
        for (Tail tail : plan.tails) {
            if (tail.nextCheckDueAt != null && tail.nextCheckDueAt.isBefore(end)) {
                MxItem item = new MxItem();
                item.reg = tail.reg;
                item.kind = "A_CHECK_DUE";
                item.dueAt = tail.nextCheckDueAt;
                item.note = tail.nextCheckLabel;
                plan.mxItems.add(item);
            }
            for (MelItemDto melItem : mel.getOrDefault(tail.aircraftId, List.of())) {
                if (melItem.blocksDispatch()) {
                    CamoItem item = new CamoItem();
                    item.reg = tail.reg;
                    item.item = "MEL " + melItem.reference() + (melItem.title() == null ? "" : " — " + melItem.title());
                    plan.camoItems.add(item);
                }
            }
        }

        // Les coordonnees, pour les distances — DATA.geo() de l'annexe, sur refdata.airports.
        Set<String> stations = new HashSet<>();
        plan.flights.forEach(f -> {
            stations.add(f.from);
            stations.add(f.to);
        });
        stations.add(plan.baseApt);
        plan.tails.forEach(t -> stations.add(t.base));
        plan.airports = stations.isEmpty() ? Map.of() : airportService.findAllByIcao(stations);
        return plan;
    }

    /* ═══════════════════ DETECTOR — Detector.scan() (l. 93212) ═════════════ */

    private void detect(Scenario scn, Cost cost) {
        Plan p = scn.plan;
        String base = p.baseApt;
        Map<String, List<Flight>> byTailDay = new HashMap<>();
        for (Flight f : p.flights) {
            byTailDay.computeIfAbsent(f.reg + "|" + f.day, k -> new ArrayList<>()).add(f);
        }
        // 1 · ferries and repositionings actually planned
        for (Flight f : p.flights) {
            if (!"FERRY".equals(f.kind) || "cancelled".equals(f.status)) {
                continue;
            }
            Anomaly a = scn.add("FERRY_UNNECESSARY", f.reg, f.day, List.of(f.id),
                    "Ferry " + f.fn + " " + f.from + "→" + f.to + " " + hhmm(f.s) + "-" + hhmm(f.e) + " · "
                            + Math.round(p.distNm(f.from, f.to)) + " NM with no revenue");
            a.airports = List.of(f.from, f.to);
            f.anomalyIds.add(a.id);
        }
        // 2 · delays already recorded
        for (Flight f : p.flights) {
            if (f.delayMin <= 0) {
                continue;
            }
            Anomaly a = scn.add("DELAY", f.reg, f.day, List.of(f.id),
                    f.fn + " running " + f.delayMin + " min late — downstream rotation at risk");
            f.anomalyIds.add(a.id);
        }
        // 3 · cancellations / AOG on the live plan
        for (Flight f : p.flights) {
            if ("cancelled".equals(f.status)) {
                Anomaly a = scn.add("CANCELLATION", f.reg, f.day, List.of(f.id),
                        f.fn + " " + f.from + "→" + f.to + " cancelled — demand unserved");
                f.anomalyIds.add(a.id);
                Request r = new Request();
                r.id = "REQ-" + p.requests.size();
                r.kind = "RECOVERY";
                r.from = f.from;
                r.to = f.to;
                r.day = f.day;
                r.window = new double[] {f.s, f.s + 6};
                r.pax = f.pax > 0 ? f.pax : 4;
                r.anomalyId = a.id;
                r.fn = f.fn;
                p.requests.add(r);
            }
        }
        for (Tail t : p.tails) {
            if (!"aog".equals(t.baseStatus) && !"maint".equals(t.baseStatus)) {
                continue;
            }
            List<Flight> mine = p.flights.stream()
                    .filter(f -> f.reg.equals(t.reg) && !"cancelled".equals(f.status) && !"GROUND".equals(f.kind))
                    .sorted(Comparator.comparingInt((Flight f) -> f.day).thenComparingDouble(f -> f.s)).toList();
            Anomaly a = scn.add("AOG", t.reg, 0, List.of(),
                    t.reg + " is " + ("aog".equals(t.baseStatus) ? "AOG" : "in maintenance") + " — " + mine.size()
                            + " planned sector(s) are uncovered and must be re-flown by another tail"
                            + (mine.isEmpty() ? "" : ": " + mine.stream().limit(4)
                            .map(f -> f.fn + " " + f.from + "→" + f.to + " D+" + f.day).collect(Collectors.joining(", "))
                            + (mine.size() > 4 ? " …" : "")));
            a.airport = base;
            for (Flight f : mine) {
                Request r = new Request();
                r.id = "REQ-" + p.requests.size();
                r.kind = "RECOVERY";
                r.from = f.from;
                r.to = f.to;
                r.day = f.day;
                r.window = new double[] {f.s, f.s + 6};
                r.pax = f.pax > 0 ? f.pax : 4;
                r.anomalyId = a.id;
                r.groundedReg = t.reg;
                r.fn = f.fn;
                p.requests.add(r);
            }
        }
        // 4 · positional inefficiency: tail ends the day away from base with no next-day departure there
        for (Tail t : p.tails) {
            for (int d = 0; d < p.days.size(); d++) {
                List<Flight> fs = active(byTailDay.get(t.reg + "|" + d));
                if (fs.isEmpty()) {
                    continue;
                }
                String eod = fs.get(fs.size() - 1).to;
                if (eod.equals(base) || eod.equals(t.base) || eod.equals(DEFAULT_BASE)) {
                    continue;
                }
                List<Flight> nextDay = active(byTailDay.get(t.reg + "|" + (d + 1)));
                Flight next = nextDay.isEmpty() ? null : nextDay.get(0);
                if (next != null && next.from.equals(eod)) {
                    continue;
                }
                Anomaly a = scn.add("MISPOSITIONED", t.reg, d, List.of(fs.get(fs.size() - 1).id),
                        t.reg + " night-stops at " + eod + " on D+" + d
                                + (next != null ? " but next leg departs " + next.from : " with no follow-on leg")
                                + " · " + Math.round(p.distNm(eod, base)) + " NM off base");
                a.airport = eod;
            }
        }
        // 5 · fleet utilization spread
        Map<String, Double> hrs = new HashMap<>();
        List<Flight> act = p.flights.stream().filter(f -> "REV".equals(f.kind) && !"cancelled".equals(f.status)).toList();
        p.tails.forEach(t -> hrs.put(t.reg, 0.0));
        act.forEach(f -> hrs.merge(f.reg, f.e - f.s, Double::sum));
        double mean = p.tails.isEmpty() ? 0 : p.tails.stream().mapToDouble(t -> hrs.getOrDefault(t.reg, 0.0)).sum() / p.tails.size();
        for (Tail t : p.tails) {
            if (!"ok".equals(t.baseStatus)) {
                continue;
            }
            double h = hrs.getOrDefault(t.reg, 0.0);
            if (h == 0) {
                scn.add("DORMANT_AC", t.reg, 0, List.of(),
                        t.reg + " has no revenue flying over the selected window — capacity idle");
            } else if (mean > 0 && h < mean * 0.45) {
                scn.add("UNDERUTILIZED_AC", t.reg, 0, List.of(),
                        t.reg + " at " + fmt1(h) + " FH vs fleet average " + fmt1(mean) + " FH — under-used");
            } else if (mean > 0 && h > mean * 1.7) {
                scn.add("OVERUTILIZED_AC", t.reg, 0, List.of(),
                        t.reg + " at " + fmt1(h) + " FH vs fleet average " + fmt1(mean) + " FH — overloaded");
            }
        }
        // 6 · rotations flown with a stop where a direct sector exists
        for (Tail t : p.tails) {
            for (int d = 0; d < p.days.size(); d++) {
                List<Flight> fs = active(byTailDay.get(t.reg + "|" + d)).stream().filter(f -> "REV".equals(f.kind)).toList();
                for (int i = 0; i < fs.size() - 1; i++) {
                    Flight a1 = fs.get(i);
                    Flight b1 = fs.get(i + 1);
                    if (!a1.to.equals(b1.from) || b1.s - a1.e > 3) {
                        continue;
                    }
                    double via = p.distNm(a1.from, a1.to) + p.distNm(b1.from, b1.to);
                    double direct = p.distNm(a1.from, b1.to);
                    if (direct > 0 && via > direct * 1.35) {
                        Anomaly a = scn.add("ROTATION_INEFFICIENT", t.reg, d, List.of(a1.id, b1.id),
                                a1.fn + "/" + b1.fn + " routed " + a1.from + "→" + a1.to + "→" + b1.to + " (" + Math.round(via)
                                        + " NM) instead of direct " + a1.from + "→" + b1.to + " (" + Math.round(direct) + " NM)");
                        a.via = a1.to;
                        a.origTo = b1.to;
                        a1.anomalyIds.add(a.id);
                        b1.anomalyIds.add(a.id);
                    }
                }
            }
        }
        // 7 · crew: FTL exposure, from the verdict recorded at assignment (ORO.FTL)
        boolean anyCrew = false;
        for (Flight f : p.flights) {
            if ("cancelled".equals(f.status) || f.crew.isEmpty()) {
                continue;
            }
            anyCrew = true;
            if ("BREACH".equals(f.crewFtl)) {
                Anomaly a = scn.add("CREW_CONFLICT", f.reg, f.day, List.of(f.id),
                        "Crew " + String.join("/", f.crew) + " outside the flight-time limitations on " + f.fn
                                + " D+" + f.day + " — per the FTL engine (ORO.FTL.205 / 235)");
                a.crew = List.copyOf(f.crew);
                a.subtype = "FDP";
                f.anomalyIds.add(a.id);
            }
            if ("EXPIRED".equals(f.crewDocs)) {
                Anomaly a = scn.add("CREW_CONFLICT", f.reg, f.day, List.of(f.id),
                        "Crew " + String.join("/", f.crew) + " — licence, medical or recurrent training expired on " + f.fn
                                + " (Part-FCL / Part-MED)");
                a.crew = List.copyOf(f.crew);
                a.subtype = "QUALIFICATION";
                f.anomalyIds.add(a.id);
            }
        }
        if (!anyCrew) {
            scn.add("CREW_REPOSITIONING", null, 0, List.of(),
                    "No published crew duty found for this window — crew optimization unavailable until the roster is published");
        }
        // 8 · maintenance / CAMO
        for (MxItem m : p.mxItems) {
            double planned = p.flights.stream().filter(f -> f.reg.equals(m.reg) && "REV".equals(f.kind) && !"cancelled".equals(f.status))
                    .mapToDouble(f -> f.e - f.s).sum();
            Anomaly a = scn.add("MX_CRITICAL", m.reg, 0, List.of(),
                    m.reg + " check due " + m.dueAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate() + " with " + fmt1(planned)
                            + " FH planned in the window" + (m.note == null ? "" : " (" + m.note + ")"));
            a.plannedHrs = planned;
            m.anomalyId = a.id;
        }
        for (CamoItem m : p.camoItems) {
            List<String> after = p.flights.stream().filter(f -> f.reg.equals(m.reg) && "REV".equals(f.kind) && !"cancelled".equals(f.status))
                    .map(f -> f.id).toList();
            Anomaly a = scn.add("CAMO_CONFLICT", m.reg, 0, after,
                    m.reg + " — " + m.item + " with " + after.size() + " sector(s) still scheduled");
            a.item = m.item;
            m.anomalyId = a.id;
        }
        // 8b · dispatch readiness — straight from the requests and the crew seats
        List<Flight> notReady = p.flights.stream()
                .filter(f -> !f.dispatchMissing.isEmpty() && !"cancelled".equals(f.status) && !"GROUND".equals(f.kind)).toList();
        for (Flight f : notReady.stream().limit(12).toList()) {
            Anomaly a = scn.add("DISPATCH_NOT_READY", f.reg, f.day, List.of(f.id),
                    f.fn + " " + f.from + "→" + f.to + " D+" + f.day + " is NOT READY in Dispatch — missing "
                            + String.join(", ", f.dispatchMissing)
                            + ". Any re-assignment of this leg must carry its dispatch file, permits and MVT with it.");
            f.anomalyIds.add(a.id);
        }
        if (notReady.size() > 12) {
            scn.add("DISPATCH_NOT_READY", null, 0, List.of(),
                    (notReady.size() - 12) + " further leg(s) are not dispatch-ready — see the Dispatch Needs-Action tab");
        }
        // 9 · airport constraints from Airport Data restrictions / category
        Set<String> seen = new HashSet<>();
        for (Flight f : p.flights) {
            for (String ap : List.of(f.from, f.to)) {
                if (!seen.add(ap)) {
                    continue;
                }
                AirportDto apt = p.airports.get(ap);
                if (apt == null) {
                    continue;
                }
                String restrictions = apt.restrictions() == null ? "" : apt.restrictions();
                String cat = apt.aerodromeCategory() == null ? "" : apt.aerodromeCategory();
                if (restrictions.matches("(?is).*(closed|prohib|crit).*")) {
                    Anomaly a = scn.add("AIRPORT_CLOSURE", null, 0, List.of(),
                            ap + " carries a critical Airport Data restriction — verify operability before dispatch: " + restrictions);
                    a.airport = ap;
                } else if (cat.matches("(?i)^[CD].*") || restrictions.matches("(?i).*restrict.*")) {
                    Anomaly a = scn.add("RWY_CLOSURE", null, 0, List.of(),
                            ap + " classified category " + (cat.isBlank() ? "restricted" : cat)
                                    + " — restricted operation, captain qualification / briefing required");
                    a.airport = ap;
                }
            }
        }
        // 10 · parking pressure — only AWAY from base
        for (int d = 0; d < p.days.size(); d++) {
            Map<String, List<String>> away = new LinkedHashMap<>();
            for (Tail t : p.tails) {
                List<Flight> fs = active(byTailDay.get(t.reg + "|" + d));
                if (fs.isEmpty()) {
                    continue;
                }
                String eod = fs.get(fs.size() - 1).to;
                if (eod.equals(base) || eod.equals(DEFAULT_BASE)) {
                    continue;
                }
                away.computeIfAbsent(eod, k -> new ArrayList<>()).add(t.reg);
            }
            for (Map.Entry<String, List<String>> entry : away.entrySet()) {
                if (entry.getValue().size() < 3) {
                    continue;
                }
                Anomaly a = scn.add("PARKING_LIMIT", null, d, List.of(),
                        entry.getValue().size() + " tails night-stopping at " + entry.getKey() + " on D+" + d
                                + " — confirm stands with the handling agent");
                a.airport = entry.getKey();
                a.tails = List.copyOf(entry.getValue());
            }
        }
        // 10b · out-and-back with a long idle gap
        for (Tail t : p.tails) {
            for (int d = 0; d < p.days.size(); d++) {
                List<Flight> fs = active(byTailDay.get(t.reg + "|" + d));
                for (int i = 0; i < fs.size() - 1; i++) {
                    Flight a1 = fs.get(i);
                    Flight b1 = fs.get(i + 1);
                    if (!a1.to.equals(b1.from) || !b1.to.equals(a1.from)) {
                        continue;
                    }
                    double idle = b1.s - a1.e;
                    if (idle < 4) {
                        continue;
                    }
                    Anomaly a = scn.add("REPOSITIONING_UNNECESSARY", t.reg, d, List.of(a1.id, b1.id),
                            t.reg + " flies " + a1.from + "→" + a1.to + " then waits " + fmt1(idle) + "h before returning (" + b1.fn + ") · "
                                    + Math.round(p.distNm(a1.from, a1.to) * 2) + " NM round trip — candidate for a shorter positioning or another tail");
                    a.airport = a1.to;
                }
            }
        }
        // 11 · CONSOLIDATION — exclusive-use rule: only the same dossier may be merged
        Map<String, List<Flight>> pairs = new LinkedHashMap<>();
        for (Flight f : act) {
            pairs.computeIfAbsent(f.day + "|" + f.from + "-" + f.to, k -> new ArrayList<>()).add(f);
        }
        for (List<Flight> fs : pairs.values()) {
            fs.sort(Comparator.comparingDouble(f -> f.s));
            for (int i = 0; i < fs.size() - 1; i++) {
                Flight a1 = fs.get(i);
                Flight b1 = fs.get(i + 1);
                if (b1.s - a1.s > 1.5) {
                    continue;
                }
                boolean sameDossier = a1.bookingRef != null && a1.bookingRef.equals(b1.bookingRef);
                if (!sameDossier) {
                    scn.exclusiveUseKept++;
                    scn.exclusiveUseDetail.add(a1.fn + " [dossier " + (a1.bookingRef == null ? "?" : a1.bookingRef)
                            + (a1.broker == null ? "" : " / " + a1.broker) + ", " + a1.pax + " pax] and " + b1.fn + " [dossier "
                            + (b1.bookingRef == null ? "?" : b1.bookingRef) + (b1.broker == null ? "" : " / " + b1.broker) + ", " + b1.pax
                            + " pax] " + a1.from + "→" + a1.to + " D+" + a1.day + " on " + a1.reg + "/" + b1.reg
                            + (a1.broker != null && a1.broker.equals(b1.broker) ? " — same broker, two dossiers" : " — two dossiers"));
                    continue;
                }
                Anomaly a = scn.add("COMBINABLE_FLIGHTS", null, a1.day, List.of(a1.id, b1.id),
                        "Dossier " + a1.bookingRef + " split over " + a1.fn + " and " + b1.fn + " " + a1.from + "→" + a1.to
                                + " within 90 min on " + a1.reg + "/" + b1.reg + " — same booking and same manifest, one aircraft is enough");
                a.airports = List.of(a1.from, a1.to);
                a1.anomalyIds.add(a.id);
                b1.anomalyIds.add(a.id);
            }
        }
        Map<String, List<Flight>> ferryPairs = new LinkedHashMap<>();
        for (Flight f : p.flights) {
            if ("FERRY".equals(f.kind) && !"cancelled".equals(f.status)) {
                ferryPairs.computeIfAbsent(f.day + "|" + f.from + "-" + f.to, k -> new ArrayList<>()).add(f);
            }
        }
        for (List<Flight> fs : ferryPairs.values()) {
            if (fs.size() < 2) {
                continue;
            }
            Anomaly a = scn.add("COMBINABLE_FLIGHTS", null, fs.get(0).day, fs.stream().map(f -> f.id).toList(),
                    fs.size() + " empty positioning legs " + fs.get(0).from + "→" + fs.get(0).to + " on D+" + fs.get(0).day + " ("
                            + fs.stream().map(f -> f.fn + "/" + f.reg).collect(Collectors.joining(", ")) + ") — no client on board, only one is needed");
            a.ferryOnly = true;
            fs.forEach(f -> f.anomalyIds.add(a.id));
        }
        for (Flight f : p.flights) {
            if (!"FERRY".equals(f.kind) || "cancelled".equals(f.status) || !f.anomalyIds.isEmpty() || p.distNm(f.from, f.to) < 250) {
                continue;
            }
            scn.add("EMPTY_LEG_OPPORTUNITY", f.reg, f.day, List.of(f.id),
                    "Empty leg " + f.fn + " " + f.from + "→" + f.to + " D+" + f.day + " " + hhmm(f.s) + " · " + Math.round(p.distNm(f.from, f.to))
                            + " NM — offer it as a discounted empty leg instead of flying it for nothing");
        }
    }

    private static List<Flight> active(List<Flight> flights) {
        if (flights == null) {
            return List.of();
        }
        return flights.stream().filter(f -> !"cancelled".equals(f.status) && !"GROUND".equals(f.kind))
                .sorted(Comparator.comparingDouble(f -> f.s)).collect(Collectors.toCollection(ArrayList::new));
    }

    /* ═══════════════════ SCORE — scorePlan() (l. 90571) ════════════════════ */

    private double scorePlan(Scenario scn, Cost cost) {
        Plan p = scn.plan;
        List<Flight> act = p.flights.stream().filter(f -> !"cancelled".equals(f.status) && !"GROUND".equals(f.kind)).toList();
        double revH = act.stream().filter(f -> "REV".equals(f.kind)).mapToDouble(f -> f.e - f.s).sum();
        double ferH = act.stream().filter(f -> "FERRY".equals(f.kind)).mapToDouble(f -> f.e - f.s).sum();
        double ferRatio = revH > 0 ? ferH / (revH + ferH) : (ferH > 0 ? 1 : 0);
        Map<String, Double> hrs = new HashMap<>();
        p.tails.forEach(t -> hrs.put(t.reg, 0.0));
        act.stream().filter(f -> "REV".equals(f.kind)).forEach(f -> hrs.merge(f.reg, f.e - f.s, Double::sum));
        double mean = p.tails.isEmpty() ? 0 : hrs.values().stream().mapToDouble(Double::doubleValue).sum() / p.tails.size();
        double sd = p.tails.isEmpty() ? 0 : Math.sqrt(hrs.values().stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / p.tails.size());
        long cxl = p.flights.stream().filter(f -> "cancelled".equals(f.status)).count();
        long conflicts = scn.anomalies.stream().filter(a -> "crit".equals(a.severity) && !a.resolved).count();
        long openReq = p.requests.stream().filter(r -> r.servedBy == null).count();
        double score = 100;
        score -= ferRatio * 70;
        score -= Math.min(12, sd * 1.2);
        score -= Math.min(18, cxl * 1.5);
        score -= Math.min(20, conflicts * 2.5);
        score -= Math.min(15, openReq * 1.2);
        if (revH > 0 && !p.tails.isEmpty()) {
            PlanCost c = cost.planCost(scn);
            double refFH = p.tails.stream().mapToDouble(t -> cost.perFH(t.type)).sum() / p.tails.size();
            double cpf = c.total / revH;
            double ratio = cpf / (refFH * 1.8);
            score += Math.max(-15, Math.min(15, (1 - ratio) * 15));
        }
        return Math.max(0, Math.min(100, Math.round(score * 10) / 10.0));
    }

    /* ═══════════════════ ENGINE — le bareme, sans tirage (l. 90787) ════════ */

    private final class Engine {
        final Scenario scn;
        final Scenario opt;
        final Cost cost;
        final String objective;
        final double target;
        final List<Action> actions = new ArrayList<>();
        int detected;
        int corrected;
        int skipped;
        boolean stopped;

        Engine(Scenario scn, Cost cost, String objective, double target) {
            this.scn = scn;
            this.opt = scn.copy();
            this.cost = cost;
            this.objective = objective;
            this.target = target;
        }

        void run() {
            List<Anomaly> work = new ArrayList<>();
            for (Anomaly a : opt.anomalies) {
                if (a.outOfScope) {
                    skipped++;
                } else {
                    work.add(a);
                }
            }
            Comparator<Anomaly> bySeverity = Comparator.comparingInt((Anomaly a) -> SEVERITY_WEIGHT.getOrDefault(a.severity, 2)).reversed()
                    .thenComparing(Comparator.comparingInt((Anomaly a) -> a.estValue).reversed());
            work.sort("COST".equals(objective) ? Comparator.comparingInt((Anomaly a) -> a.estValue).reversed() : bySeverity);
            double runningValue = 0;
            for (Anomaly a : work) {
                detected++;
                if (stopped) {
                    continue;
                }
                List<Flight> refFlights = a.flights.stream().map(opt.plan::byId).filter(f -> f != null).toList();
                if ("COMBINABLE_FLIGHTS".equals(a.type)) {
                    List<Flight> live = refFlights.stream().filter(f -> !"cancelled".equals(f.status)).toList();
                    if (!mergeAllowed(live)) {
                        refuseMerge(a, live);
                        continue;
                    }
                }
                a.resolved = true;
                corrected++;
                resolve(a, refFlights);
                opt.plan.requests.stream().filter(r -> r.anomalyId.equals(a.id) && r.servedBy == null).forEach(r -> r.servedBy = "OPT");
                runningValue += a.estValue;
                if (target > 0 && "COST".equals(objective) && runningValue >= target) {
                    stopped = true;
                }
                if (target > 0 && "SCORE".equals(objective) && corrected >= Math.ceil(work.size() * target / 100)) {
                    stopped = true;
                }
            }
            opt.plan.flights.removeIf(f -> "cancelled-optimized".equals(f.status));
        }

        private boolean mergeAllowed(List<Flight> legs) {
            if (legs.size() < 2) {
                return false;
            }
            boolean ferryOnly = legs.stream().allMatch(f -> "FERRY".equals(f.kind) || (f.bookingRef == null && f.pax == 0));
            String bk = legs.get(0).bookingRef;
            boolean sameDossier = bk != null && legs.stream().allMatch(f -> bk.equals(f.bookingRef));
            return ferryOnly || sameDossier;
        }

        private void refuseMerge(Anomaly a, List<Flight> legs) {
            act(a, "NO_MERGE_EXCLUSIVE_USE", legs.stream().map(this::fDesc).toList(), legs.stream().map(f -> f.reg).toList(), List.of(), Map.of(),
                    "Refused — " + legs.stream().map(f -> f.fn + " [dossier " + (f.bookingRef == null ? "?" : f.bookingRef)
                            + (f.broker == null ? "" : ", broker " + f.broker) + ", " + f.pax + " pax]").collect(Collectors.joining(" vs "))
                            + ". Different dossiers: each dossier charters the whole aircraft. Keep both tails, do not mix the manifests.");
        }

        private void resolve(Anomaly a, List<Flight> refFlights) {
            Plan plan = opt.plan;
            String t = a.type;
            switch (t) {
                case "FERRY_UNNECESSARY", "REPOSITIONING_UNNECESSARY", "REPOSITIONING_AFTER_CXL", "FAR_FROM_DEMAND", "SAME_APT_CLUSTER" -> {
                    double hrs = 0;
                    double nm = 0;
                    List<String> fl = new ArrayList<>();
                    Set<String> ac = new LinkedHashSet<>();
                    for (Flight f : refFlights) {
                        if (!"FERRY".equals(f.kind)) {
                            continue;
                        }
                        hrs += f.e - f.s;
                        nm += plan.distNm(f.from, f.to);
                        fl.add(fDesc(f));
                        ac.add(f.reg);
                        f.status = "cancelled-optimized";
                    }
                    if (fl.isEmpty()) {
                        // Un aller-retour avec attente : la valeur est celle du positionnement, la
                        // decision (autre appareil, positionnement plus court) reste au dispatcher.
                        act(a, "REMOVE_FERRY", refFlights.stream().map(this::fDesc).toList(), refFlights.stream().map(f -> f.reg).distinct().toList(), List.of(),
                                Map.of(), "Advisory — " + a.note + ". Review whether a shorter positioning or another tail serves this rotation.");
                    } else {
                        act(a, "REMOVE_FERRY", fl, new ArrayList<>(ac), List.of(),
                                Map.of("ferryHrsSaved", round2(hrs), "nmAvoided", Math.round(nm)),
                                "Ferry leg(s) deleted — aircraft kept on stand, " + fmt1(hrs) + " FH and " + Math.round(nm) + " NM saved");
                    }
                }
                case "DELAY" -> {
                    Flight f0 = refFlights.isEmpty() ? null : refFlights.get(0);
                    if (f0 != null && f0.delayMin > 0) {
                        // Ce qui se recupere en compressant les escales : la marge reelle
                        // devant l'etape suivante, pas un tirage.
                        Flight next = plan.flights.stream()
                                .filter(x -> x.reg.equals(f0.reg) && x.day == f0.day && x.s > f0.s && !"cancelled".equals(x.status))
                                .min(Comparator.comparingDouble(x -> x.s)).orElse(null);
                        double minimumH = ops.getMinimumTurnaround().toMinutes() / 60.0;
                        int recovered = next == null ? f0.delayMin
                                : (int) Math.max(0, Math.min(f0.delayMin, Math.round((next.s - f0.e - minimumH) * 60)));
                        double dh = recovered / 60.0;
                        for (Flight x : plan.flights) {
                            if (x.reg.equals(f0.reg) && x.day == f0.day && x.s >= f0.s && !"cancelled".equals(x.status)) {
                                x.s = round2(x.s - dh);
                                x.e = round2(x.e - dh);
                            }
                        }
                        int total = f0.delayMin;
                        f0.delayMin -= recovered;
                        act(a, "RESEQUENCE", List.of(fDesc(f0)), List.of(f0.reg), List.of(), Map.of("delayRecoveredMin", recovered),
                                "Turnarounds compressed on " + f0.reg + " — " + recovered + " min of the " + total + " min delay recovered");
                    } else {
                        act(a, "RESEQUENCE", List.of(), List.of(), List.of(), Map.of(), "Downstream sequence re-timed");
                    }
                }
                case "CANCELLATION", "AOG", "AIRPORT_CLOSURE" -> {
                    List<String> flights = new ArrayList<>();
                    List<String> aircraft = new ArrayList<>();
                    List<String> desc = new ArrayList<>();
                    for (Request r : plan.requests) {
                        if (!r.anomalyId.equals(a.id) || r.servedBy != null) {
                            continue;
                        }
                        String family = a.reg == null ? "FALCON" : plan.tail(a.reg) == null ? "FALCON" : plan.tail(a.reg).family;
                        String reg = findRecoveryTail(family, r.day, r.window[0], r.window[1], a.reg, r.from, r.to);
                        if (reg == null) {
                            continue;
                        }
                        r.servedBy = reg;
                        double bh = plan.blockH(r.from, r.to);
                        Flight nf = new Flight();
                        nf.createdByEngine = true;
                        nf.id = "FR" + plan.flights.size();
                        nf.day = r.day;
                        nf.date = r.day < plan.days.size() ? plan.days.get(r.day) : "";
                        nf.reg = reg;
                        nf.type = plan.tail(reg) == null ? "" : plan.tail(reg).type;
                        nf.fn = (r.fn == null ? "TNP" : r.fn) + "R";
                        nf.from = r.from;
                        nf.to = r.to;
                        nf.s = r.window[0];
                        nf.e = round2(r.window[0] + bh);
                        nf.status = "scheduled";
                        nf.kind = "REV";
                        nf.pax = r.pax;
                        nf.anomalyIds.add(a.id);
                        nf.label = "Recovery";
                        plan.flights.add(nf);
                        flights.add(r.from + "→" + r.to + " D+" + r.day + " (" + r.pax + " pax)");
                        aircraft.add(reg);
                        desc.add("Demand " + r.from + "→" + r.to + " re-flown by " + reg + " — crew to be paired in Crew Scheduling (FTL engine)");
                    }
                    if (!flights.isEmpty()) {
                        act(a, "AOG".equals(t) ? "REASSIGN_FLEET" : "RECOVER_DEMAND", flights, aircraft, List.of(),
                                Map.of("demandsRecovered", flights.size()), String.join(" · ", desc));
                    } else {
                        act(a, "RECOVER_DEMAND", refFlights.stream().map(this::fDesc).toList(), a.reg == null ? List.of() : List.of(a.reg), List.of(),
                                Map.of(), "Recovery attempted — no qualified tail free in the window");
                    }
                }
                case "CREW_CONFLICT" -> {
                    // Le serveur ne tient pas de vivier de remplacement : l'annexe cherchait un
                    // collegue dans crewData. Dire ou cela se resout vaut mieux qu'un nom tire au sort.
                    Flight f2 = refFlights.isEmpty() ? null : refFlights.get(0);
                    act(a, "REASSIGN_CREW", refFlights.stream().map(this::fDesc).toList(), refFlights.stream().map(f -> f.reg).toList(),
                            a.crew, Map.of("critConflictsCleared", 1),
                            "Relieve " + String.join("/", a.crew) + " on " + (f2 == null ? "this flight" : f2.fn)
                                    + " — pick the replacement in Crew Scheduling, where only legal candidates (FTL, rest, roster) are offered. The rest of the crew is unchanged.");
                }
                case "UNDERUTILIZED_CREW", "OVERUTILIZED_CREW" -> {
                    String cid = a.crew.isEmpty() ? "?" : a.crew.get(0);
                    act(a, "REBALANCE_CREW", List.of(), List.of(), a.crew, Map.of(),
                            "UNDERUTILIZED_CREW".equals(t) ? cid + " available in this window — use for the next pairing (advisory, no published duty to move)"
                                    : cid + " overloaded — no rested qualified colleague available in the window (advisory)");
                }
                case "MISPOSITIONED" -> {
                    double hrs2 = 0;
                    double nm2 = 0;
                    List<String> fl2 = new ArrayList<>();
                    Set<String> ac2 = new LinkedHashSet<>();
                    for (Flight f : refFlights) {
                        if ("FERRY".equals(f.kind)) {
                            hrs2 += f.e - f.s;
                            nm2 += plan.distNm(f.from, f.to);
                            fl2.add(fDesc(f));
                            ac2.add(f.reg);
                            f.status = "cancelled-optimized";
                        }
                    }
                    if (!fl2.isEmpty()) {
                        act(a, "REPOSITION_OPTIMALLY", fl2, new ArrayList<>(ac2), List.of(), Map.of("ferryHrsSaved", round2(hrs2), "nmAvoided", Math.round(nm2)),
                                "Cancel the positioning leg — hold the aircraft at base and serve next-day demand directly");
                    } else {
                        String apt2 = a.airport == null ? "?" : a.airport;
                        long homeNm = Math.round(plan.distNm(apt2, plan.baseApt));
                        act(a, "REPOSITION_OPTIMALLY", refFlights.stream().map(this::fDesc).toList(), List.of(a.reg == null ? "?" : a.reg), List.of(),
                                Map.of("nmAvoided", homeNm, "standsFreed", 0),
                                "Advisory — " + a.reg + " night-stops at " + apt2 + " (" + homeNm + " NM off base): plan the next rotation from "
                                        + apt2 + ", or swap tails so the aircraft ends the day at " + plan.baseApt);
                    }
                }
                case "ROTATION_INEFFICIENT" -> {
                    if (refFlights.size() >= 2) {
                        Flight legA = refFlights.get(0);
                        Flight legB = refFlights.get(1);
                        double direct = plan.blockH(legA.from, legB.to);
                        double saved = round2((legA.e - legA.s) + (legB.e - legB.s) - direct);
                        long nmS = Math.round(plan.distNm(legA.from, legA.to) + plan.distNm(legB.from, legB.to) - plan.distNm(legA.from, legB.to));
                        String origTo = legB.to;
                        legA.to = origTo;
                        legA.e = round2(legA.s + direct);
                        legB.status = "cancelled-optimized";
                        act(a, "DIRECT_ROUTING", List.of(legA.fn + " re-routed direct " + legA.from + "→" + origTo + " D+" + legA.day), List.of(legA.reg), List.of(),
                                Map.of("blockHrsSaved", Math.max(0, saved), "nmAvoided", Math.max(0, nmS)),
                                "Via-stop removed (" + (a.via == null ? "?" : a.via) + ") — " + fmt1(Math.max(0, saved)) + " FH and " + Math.max(0, nmS) + " NM saved");
                    } else {
                        act(a, "DIRECT_ROUTING", List.of(), List.of(), List.of(), Map.of(), "Rotation restored to direct routing");
                    }
                }
                case "UNDERUTILIZED_AC", "DORMANT_AC", "OVERUTILIZED_AC" -> {
                    String target0 = a.reg;
                    Tail t0 = plan.tail(target0);
                    if (t0 != null && !"ok".equals(t0.baseStatus)) {
                        act(a, "REBALANCE_FLYING", List.of(), List.of(target0), List.of(), Map.of(),
                                "Advisory — " + target0 + " is " + ("aog".equals(t0.baseStatus) ? "AOG" : "in maintenance") + ": no flying can be assigned to it until it is serviceable");
                        return;
                    }
                    String fam0 = t0 == null ? "OTHER" : t0.family;
                    Map<String, Double> hrsBy = new HashMap<>();
                    plan.flights.stream().filter(f -> "REV".equals(f.kind) && !"cancelled".equals(f.status)).forEach(f -> hrsBy.merge(f.reg, f.e - f.s, Double::sum));
                    List<Tail> donors = plan.tails.stream().filter(x -> x.family.equals(fam0) && !x.reg.equals(target0) && "ok".equals(x.baseStatus))
                            .sorted(Comparator.comparingDouble((Tail x) -> hrsBy.getOrDefault(x.reg, 0.0)).reversed()).toList();
                    boolean over = "OVERUTILIZED_AC".equals(t);
                    String receiver = over ? (donors.isEmpty() ? null : donors.get(donors.size() - 1).reg) : target0;
                    String donor = over ? target0 : (donors.isEmpty() ? null : donors.get(0).reg);
                    List<String> moved = new ArrayList<>();
                    if (donor != null && receiver != null && !donor.equals(receiver)) {
                        List<Flight> cand = plan.flights.stream().filter(f -> f.reg.equals(donor) && "REV".equals(f.kind) && !"cancelled".equals(f.status))
                                .sorted(Comparator.comparingDouble((Flight f) -> f.e - f.s).reversed()).toList();
                        for (Flight f2 : cand) {
                            if (moved.size() >= 2) {
                                break;
                            }
                            if (!canHostLeg(receiver, f2) || plan.distNm(f2.from, f2.to) > 4500) {
                                continue;
                            }
                            if (f2.movedFrom == null) {
                                f2.movedFrom = f2.reg;
                            }
                            f2.reg = receiver;
                            f2.anomalyIds.add(a.id);
                            moved.add(f2.fn + " " + f2.from + "→" + f2.to + " D+" + f2.day + " : " + donor + " → " + receiver);
                        }
                    }
                    List<String> aircraft = new ArrayList<>();
                    if (donor != null) {
                        aircraft.add(donor);
                    }
                    if (receiver != null) {
                        aircraft.add(receiver);
                    }
                    act(a, "REBALANCE_FLYING", moved, aircraft, List.of(), moved.isEmpty() ? Map.of() : Map.of("sectorsReassigned", moved.size()),
                            !moved.isEmpty() ? moved.size() + " sector(s) transferred to level utilization — " + String.join(" ; ", moved)
                                    : ("DORMANT_AC".equals(t) ? target0 + " stays idle — no transferable sector found in the window (advisory: seek charter demand)"
                                    : "No conflict-free transfer available in the window (advisory)"));
                }
                case "EMPTY_LEG_OPPORTUNITY" -> {
                    Flight el = refFlights.isEmpty() ? null : refFlights.get(0);
                    act(a, "MARKET_EMPTY_LEG", el == null ? List.of() : List.of(fDesc(el)), List.of(a.reg == null ? "?" : a.reg), List.of(), Map.of("emptyLegOffered", 1),
                            "Advisory — publish " + (el == null ? "this empty leg" : el.fn + " " + el.from + "→" + el.to)
                                    + " as an empty-leg offer through Sales / brokers. The aircraft flies it anyway, so any revenue is upside. Never place another client on a booked flight — charter is exclusive use.");
                }
                case "VIP_REQUEST", "COMBINABLE_FLIGHTS" -> {
                    List<Flight> legs2 = refFlights.stream().filter(f -> !"cancelled".equals(f.status)).toList();
                    if (legs2.size() >= 2) {
                        Flight keep = legs2.get(0);
                        Flight drop = legs2.get(1);
                        keep.pax += drop.pax;
                        drop.status = "cancelled-optimized";
                        String why = "FERRY".equals(drop.kind) ? "empty positioning legs, nobody on board" : "same dossier " + keep.bookingRef + " and same manifest";
                        act(a, "MERGE_SAME_CLIENT", List.of(fDesc(keep) + "  (keep)", fDesc(drop) + "  (cancel)"), List.of(keep.reg, drop.reg), List.of(),
                                Map.of("flightsMerged", 2, "ferryHrsSaved", round2(Math.max(0, drop.e - drop.s)), "nmAvoided", Math.round(plan.distNm(drop.from, drop.to))),
                                "Consolidate " + drop.fn + " into " + keep.fn + " on " + keep.reg + " — " + why + " (" + keep.pax + " pax on one tail); " + drop.reg + " released");
                    } else {
                        act(a, "ASSIGN_AIRCRAFT", List.of(), a.reg == null ? List.of() : List.of(a.reg), List.of(), Map.of(),
                                "No free qualified tail in the requested window — consider a sub-charter or re-timing the demand");
                    }
                }
                case "DISPATCH_NOT_READY" -> {
                    Flight dl = refFlights.isEmpty() ? null : refFlights.get(0);
                    act(a, "COMPLETE_DISPATCH", dl == null ? List.of() : List.of(fDesc(dl)), List.of(a.reg == null ? "?" : a.reg), List.of(), Map.of(),
                            "Advisory — " + a.note);
                }
                case "MX_CRITICAL" -> {
                    plan.mxItems.stream().filter(m -> a.id.equals(m.anomalyId)).forEach(m -> m.resolved = true);
                    act(a, "RESCHEDULE_MX", List.of(), List.of(a.reg == null ? "?" : a.reg), List.of(), Map.of("fhProtected", round1(a.plannedHrs)),
                            "Book the check slot for " + a.reg + " and trim its flying — " + fmt1(a.plannedHrs) + " FH planned before the due date");
                }
                case "CAMO_CONFLICT" -> {
                    plan.camoItems.stream().filter(m -> a.id.equals(m.anomalyId)).forEach(m -> m.resolved = true);
                    List<String> moved = new ArrayList<>();
                    for (Flight f : refFlights) {
                        if ("cancelled".equals(f.status) || !f.reg.equals(a.reg)) {
                            continue;
                        }
                        String alt = findRecoveryTail(fleetFamily(f.type), f.day, f.s, f.e, a.reg, f.from, f.to);
                        if (alt != null) {
                            if (f.movedFrom == null) {
                                f.movedFrom = f.reg;
                            }
                            f.reg = alt;
                            f.anomalyIds.add(a.id);
                            moved.add(f.fn + " D+" + f.day + " → " + alt);
                        }
                    }
                    act(a, "GROUND_OR_RESCHEDULE", refFlights.stream().limit(4).map(this::fDesc).toList(), List.of(a.reg == null ? "?" : a.reg), List.of(),
                            Map.of("camoConflictsCleared", 1),
                            "Clear " + (a.item == null ? "item" : a.item) + " on " + a.reg + " before it bites"
                                    + (moved.isEmpty() ? " — no compliant tail free, ground the aircraft or advance the task" : " — sectors re-assigned: " + String.join(", ", moved)));
                }
                case "WX_IMPACT", "RWY_CLOSURE" -> act(a, "REROUTE_OR_DELAY", refFlights.stream().limit(4).map(this::fDesc).toList(),
                        refFlights.stream().map(f -> f.reg).distinct().toList(), List.of(), Map.of("flightsProtected", refFlights.size()),
                        refFlights.size() + " rotations re-timed around the " + (a.airport == null ? "?" : a.airport) + " restriction window");
                case "PARKING_LIMIT" -> act(a, "REDISTRIBUTE_PARKING", List.of(), a.tails, List.of(), Map.of("standsFreed", Math.max(1, a.tails.size() - 2)),
                        "Advisory — D+" + a.day + ": " + a.tails.size() + " tails (" + String.join(", ", a.tails) + ") night-stop at " + a.airport + ". Confirm "
                                + a.tails.size() + " stands with the handling agent and send the parking request; if stands are refused, bring the surplus tail(s) back to "
                                + plan.baseApt + " the same evening");
                case "CREW_REPOSITIONING" -> act(a, "OPTIMIZE_CREW_POS", List.of(), List.of(), a.crew, Map.of(),
                        "Advisory — " + a.note);
                default -> act(a, "REVIEW", List.of(), List.of(), List.of(), Map.of(), "Generic resolution applied");
            }
        }

        /** {@code canHostLeg} (l. 90805) : pas de teleportation. */
        private boolean canHostLeg(String reg, Flight leg) {
            List<Flight> fs = opt.plan.flights.stream()
                    .filter(f -> f.reg.equals(reg) && f.day == leg.day && !f.id.equals(leg.id) && !"cancelled".equals(f.status) && !"GROUND".equals(f.kind))
                    .sorted(Comparator.comparingDouble(f -> f.s)).toList();
            Flight prev = null;
            Flight next = null;
            for (Flight f : fs) {
                if (f.e <= leg.s + 0.001) {
                    prev = f;
                }
                if (f.s >= leg.e - 0.001 && next == null) {
                    next = f;
                }
                if (f.s < leg.e - 0.01 && f.e > leg.s + 0.01) {
                    return false;
                }
            }
            if (prev != null && !prev.to.equals(leg.from)) {
                return false;
            }
            return next == null || next.from.equals(leg.to);
        }

        private String findRecoveryTail(String family, int day, double s, double e, String exclude, String from, String to) {
            Set<String> busy = new HashSet<>();
            opt.plan.flights.stream().filter(x -> x.day == day && !"cancelled".equals(x.status) && !"GROUND".equals(x.kind) && x.s < e && x.e > s)
                    .forEach(x -> busy.add(x.reg));
            for (Tail x : opt.plan.tails) {
                if (!x.family.equals(family) || !"ok".equals(x.baseStatus) || busy.contains(x.reg) || x.reg.equals(exclude)) {
                    continue;
                }
                Flight probe = new Flight();
                probe.id = "__probe";
                probe.day = day;
                probe.s = s;
                probe.e = e;
                probe.from = from;
                probe.to = to;
                probe.kind = "REV";
                probe.status = "scheduled";
                if (canHostLeg(x.reg, probe)) {
                    return x.reg;
                }
            }
            return null;
        }

        private String fDesc(Flight f) {
            return f.fn + " " + f.from + "→" + f.to + " D+" + f.day + " " + hhmm(f.s) + "-" + hhmm(f.e);
        }

        private void act(Anomaly a, String action, List<String> flights, List<String> aircraft, List<String> crew, Map<String, Object> gain, String description) {
            Action x = new Action();
            x.anomalyId = a.id;
            x.type = a.type;
            x.label = a.label;
            x.action = action;
            x.flights = flights;
            x.aircraft = aircraft;
            x.crew = crew;
            x.gain = new LinkedHashMap<>(gain);
            x.description = description;
            x.advisory = description.toLowerCase(Locale.ROOT).contains("advisory")
                    || (flights.isEmpty() && crew.isEmpty() && List.of("REDISTRIBUTE_PARKING", "REBALANCE_CREW", "REBALANCE_FLYING", "OPTIMIZE_CREW_POS").contains(action));
            actions.add(x);
        }
    }

    /* ═══════════════════ OPS — deriveOps() (l. 93592) ══════════════════════ */

    private List<Operation> deriveOps(Scenario before, Scenario after, List<Action> actions) {
        List<Operation> ops = new ArrayList<>();
        Map<String, Flight> aMap = after.plan.flights.stream().collect(Collectors.toMap(f -> f.id, f -> f, (x, y) -> x));
        Map<String, String> causeByLeg = new HashMap<>();
        for (Anomaly a : before.anomalies) {
            String label = a.label + (a.item == null ? "" : " — " + a.item);
            a.flights.forEach(fid -> causeByLeg.putIfAbsent(fid, label));
        }
        int seq = 0;
        for (Flight b : before.plan.flights) {
            Flight a = aMap.get(b.id);
            String cause = causeByLeg.get(b.id);
            if (a == null) {
                boolean ferry = "FERRY".equals(b.kind);
                Operation o = new Operation();
                o.id = "OP" + (++seq);
                o.op = "CANCEL_LEG";
                o.date = b.date;
                o.fn = b.fn;
                o.reg = b.reg;
                o.legId = b.legId;
                o.applicable = ferry;
                o.ferry = ferry;
                o.anomalyId = firstAnomaly(b);
                o.brief = ferry ? "Ferry " + b.from + " → " + b.to + " on " + dayLabel(b.date) + " (" + b.reg + ")"
                        : "Flight " + b.fn + " " + b.from + "→" + b.to + " on " + dayLabel(b.date) + " (" + b.reg + ")";
                o.title = "Cancel " + b.fn + " on " + b.reg;
                o.text = "CANCEL " + legRef(b) + " — aircraft " + b.reg + ". Reason: " + (cause == null ? "removed by optimization" : cause)
                        + (ferry ? ". The leg stays visible on the Timeline with status CANCELLED."
                        : ". This leg carries passengers: cancel it ONLY together with the re-routing that absorbs it — re-file the flight plan in Dispatch, move the manifest, then cancel. Not applied automatically.");
                ops.add(o);
                continue;
            }
            if (!a.reg.equals(b.reg)) {
                Operation o = new Operation();
                o.id = "OP" + (++seq);
                o.op = "MOVE_LEG";
                o.date = b.date;
                o.fn = b.fn;
                o.reg = b.reg;
                o.fromReg = b.reg;
                o.toReg = a.reg;
                o.legId = b.legId;
                o.applicable = true;
                o.anomalyId = firstAnomaly(b);
                o.title = "Swap " + b.fn + ": " + b.reg + " → " + a.reg;
                o.brief = "Flight " + b.fn + ": use " + a.reg + " instead of " + b.reg + " · " + dayLabel(b.date);
                o.text = "SWAP AIRCRAFT — " + legRef(b) + " moves from " + b.reg + " to " + a.reg + ". Reason: "
                        + (cause == null ? "fleet utilization rebalancing decided by the engine" : cause)
                        + (b.dispatchMissing.isEmpty() ? "" : " ⚠ Dispatch status NOT READY (missing " + String.join(", ", b.dispatchMissing)
                        + ") — carry the dispatch file, permits and MVT over to the new tail.")
                        + " Advise crew and update the trip sheet.";
                ops.add(o);
            }
            boolean routeChanged = !a.to.equals(b.to) || !a.from.equals(b.from);
            boolean laterDeparture = a.s > b.s + 0.01;
            if (!routeChanged && !laterDeparture && (Math.abs(a.s - b.s) > 0.02 || Math.abs(a.e - b.e) > 0.02)
                    && (!hhmm(a.s).equals(hhmm(b.s)) || !hhmm(a.e).equals(hhmm(b.e)))) {
                Operation o = new Operation();
                o.id = "OP" + (++seq);
                o.op = "RETIME_LEG";
                o.date = b.date;
                o.fn = b.fn;
                o.reg = a.reg;
                o.legId = b.legId;
                o.applicable = true;
                o.anomalyId = firstAnomaly(b);
                o.oldStd = b.std;
                o.oldSta = b.sta;
                o.newStd = b.std.plusMinutes(Math.round((a.s - b.s) * 60));
                o.newSta = b.sta.plusMinutes(Math.round((a.e - b.e) * 60));
                o.title = "Re-time " + b.fn + " on " + a.reg;
                String std = !hhmm(a.s).equals(hhmm(b.s)) ? "STD " + hhmm(b.s) + " → " + hhmm(a.s) : "";
                String sta = !hhmm(a.e).equals(hhmm(b.e)) ? "STA " + hhmm(b.e) + " → " + hhmm(a.e) : "";
                o.brief = "Flight " + b.fn + ": " + std + (!std.isEmpty() && !sta.isEmpty() ? ", " : "") + sta + " on " + dayLabel(b.date) + " (" + a.reg + ")";
                o.text = "RE-TIME " + b.fn + " " + b.from + "→" + b.to + " on " + a.reg + " (" + b.date + "): STD " + hhmm(b.s) + " → " + hhmm(a.s)
                        + ", STA " + hhmm(b.e) + " → " + hhmm(a.e) + " UTC" + (b.delayMin > 0 && a.delayMin == 0 ? " (delay absorbed — clear the OCC delay code)" : "")
                        + ". Re-file the flight plan and re-confirm the slot if one is held.";
                ops.add(o);
            }
            if (routeChanged) {
                Operation o = new Operation();
                o.id = "OP" + (++seq);
                o.op = "REROUTE_LEG";
                o.date = b.date;
                o.fn = b.fn;
                o.reg = a.reg;
                o.legId = b.legId;
                o.applicable = false;
                o.anomalyId = firstAnomaly(b);
                long nm = Math.round(before.plan.distNm(a.from, a.to));
                o.title = "Re-route " + b.fn + " direct " + a.from + "→" + a.to;
                o.brief = "Flight " + b.fn + ": file " + a.from + " → " + a.to + " instead of " + b.from + " → " + b.to + ", block " + hhmm(a.s) + "–" + hhmm(a.e) + " UTC (" + nm + " NM direct)";
                o.text = "RE-ROUTE " + b.fn + " on " + a.reg + " (" + b.date + "): " + b.from + "→" + b.to + " becomes " + a.from + "→" + a.to + ", block " + hhmm(a.s) + "–" + hhmm(a.e)
                        + " UTC (" + nm + " NM direct). Cancel the intermediate sector, re-file the ATC flight plan in Dispatch and re-issue the briefing — this one must be done in the Dispatch module (route is not a Timeline field).";
                ops.add(o);
            }
            if (a.pax != b.pax) {
                Operation o = new Operation();
                o.id = "OP" + (++seq);
                o.op = "SET_PAX";
                o.date = b.date;
                o.fn = b.fn;
                o.reg = a.reg;
                o.legId = b.legId;
                o.applicable = false;
                o.anomalyId = firstAnomaly(b);
                o.title = "Pax transfer on " + b.fn;
                o.brief = "Flight " + b.fn + ": manifest now " + a.pax + " pax (was " + b.pax + ") — recheck MTOW/CG";
                o.text = "PAX — " + b.fn + " on " + a.reg + " now carries " + a.pax + " pax (was " + b.pax + "): move the manifest, re-check MTOW/CG and update catering with the handler.";
                ops.add(o);
            }
        }
        for (Flight nf : after.plan.flights) {
            if (!nf.createdByEngine) {
                continue;
            }
            Operation o = new Operation();
            o.id = "OP" + (++seq);
            o.op = "ADD_LEG";
            o.date = nf.date;
            o.fn = nf.fn;
            o.reg = nf.reg;
            o.applicable = false;
            o.anomalyId = nf.anomalyIds.isEmpty() ? null : nf.anomalyIds.get(0);
            o.title = "Create recovery flight " + nf.fn + " on " + nf.reg;
            o.brief = "Recovery " + nf.from + " → " + nf.to + " " + hhmm(nf.s) + "–" + hhmm(nf.e) + " UTC on " + dayLabel(nf.date) + " (" + nf.reg + ", " + nf.pax + " pax)";
            o.text = "CREATE " + nf.fn + " " + nf.from + "→" + nf.to + " " + hhmm(nf.s) + "–" + hhmm(nf.e) + " UTC on " + nf.reg
                    + " to re-fly the uncovered demand. File it through Sales (new leg on the dossier), then pair the crew in Crew Scheduling.";
            ops.add(o);
        }
        return ops;
    }

    private static String firstAnomaly(Flight f) {
        return f.anomalyIds.isEmpty() ? null : f.anomalyIds.get(0);
    }

    /* ═══════════════════ FOLLOW-UPS — deriveFollowUps() (l. 93823) ═════════ */

    private List<FollowUp> deriveFollowUps(List<Action> actions) {
        List<FollowUp> out = new ArrayList<>();
        for (Action x : actions) {
            String reg1 = x.aircraft.isEmpty() ? "?" : x.aircraft.get(0);
            String flight1 = x.flights.isEmpty() ? reg1 : String.join(" ", List.of(x.flights.get(0).split(" ")).subList(0, Math.min(3, x.flights.get(0).split(" ").length)));
            switch (x.action) {
                case "RESCHEDULE_MX" -> out.add(new FollowUp("FU-" + out.size(), "CAMO",
                        reg1 + ": book the check slot" + (x.gain.get("fhProtected") == null ? "" : " (" + x.gain.get("fhProtected") + " FH planned)") + " and trim the flying",
                        "Book the maintenance slot for " + reg1 + " — " + x.description + ". Record it in CAMO Admin (work order + planned down-time) so the Timeline blocks the tail."));
                case "GROUND_OR_RESCHEDULE" -> out.add(new FollowUp("FU-" + out.size(), "CAMO",
                        reg1 + ": clear the airworthiness item before it bites",
                        x.description + " — close the item in the CAMO module (task card / AD compliance) and attach the release."));
                case "COMPLETE_DISPATCH" -> out.add(new FollowUp("FU-" + out.size(), "Dispatch",
                        // L'anomalie de synthese (« N further legs… ») n'a ni vol ni appareil :
                        // elle renvoie a l'onglet, pas a « ? ».
                        x.flights.isEmpty() ? "Dispatch Needs-Action tab: the remaining legs still need permits, MVT and crew slots"
                                : flight1 + ": complete permits, MVT and crew slots", x.description));
                case "REDISTRIBUTE_PARKING" -> out.add(new FollowUp("FU-" + out.size(), "Ground handling",
                        "Station: confirm " + Math.max(1, x.aircraft.size()) + " stands with the handling agent", x.description));
                case "REPOSITION_OPTIMALLY" -> {
                    if (x.advisory) {
                        out.add(new FollowUp("FU-" + out.size(), "Planning", reg1 + ": plan the next rotation from its night-stop station", x.description));
                    }
                }
                case "REBALANCE_CREW" -> out.add(new FollowUp("FU-" + out.size(), "Crew",
                        (x.crew.isEmpty() ? "Crew" : x.crew.get(0)) + ": available for the next pairing", x.description.replaceAll("(?i)\\(advisory[^)]*\\)", "").trim()));
                case "REASSIGN_CREW" -> out.add(new FollowUp("FU-" + out.size(), "Crew",
                        (x.crew.isEmpty() ? "Crew" : String.join("/", x.crew)) + ": relieve in Crew Scheduling (FTL-checked candidates)", x.description));
                case "MARKET_EMPTY_LEG" -> out.add(new FollowUp("FU-" + out.size(), "Sales",
                        flight1 + ": publish as a discounted empty leg", x.description));
                default -> {
                }
            }
        }
        return out;
    }

    /* ═══════════════════ ACTION PLAN — buildActionPlan() (l. 92115) ════════ */

    private List<String> buildActionPlan(List<Action> actions) {
        List<String> steps = new ArrayList<>();
        for (Action x : actions) {
            String ac = String.join(", ", x.aircraft);
            String fl = String.join(" ; ", x.flights);
            switch (x.action) {
                case "REMOVE_FERRY" -> {
                    if (x.flights.isEmpty()) {
                        steps.add("REVIEW positioning on " + ac + " — " + x.description);
                    }
                    for (int i = 0; i < x.flights.size(); i++) {
                        String reg = i < x.aircraft.size() ? x.aircraft.get(i) : ac;
                        steps.add("DELETE ferry " + x.flights.get(i) + (reg.isEmpty() ? "" : " — aircraft " + reg + " stays on stand"));
                    }
                }
                case "RECOVER_DEMAND", "REASSIGN_FLEET", "ASSIGN_AIRCRAFT" -> {
                    if (x.flights.isEmpty()) {
                        steps.add("RECOVER demand on " + ac + " — " + x.description);
                    }
                    for (int i = 0; i < x.flights.size(); i++) {
                        steps.add("ASSIGN aircraft " + (i < x.aircraft.size() ? x.aircraft.get(i) : ac) + " to demand " + x.flights.get(i));
                    }
                }
                case "REASSIGN_CREW" -> steps.add("REPLACE crew " + String.join("/", x.crew) + " on " + fl + " — " + x.description);
                case "RESEQUENCE" -> steps.add("RE-TIME rotation " + fl + " on " + ac + " — compress turnarounds to absorb the delay");
                case "DIRECT_ROUTING" -> steps.add("RE-ROUTE " + fl + " — file direct, remove the via-stop");
                case "MERGE_SAME_CLIENT" -> steps.add("CONSOLIDATE " + fl + " onto ONE rotation with aircraft " + ac + " — same booking only (exclusive use respected)");
                case "NO_MERGE_EXCLUSIVE_USE" -> steps.add("DO NOT MERGE " + fl + " — different bookings: each client charters the whole aircraft. Keep aircraft " + ac + " as planned");
                case "MARKET_EMPTY_LEG" -> steps.add("OFFER EMPTY LEG " + fl + " on " + ac + " to Sales / brokers — discounted empty-leg sale, no impact on the booked client");
                case "REBALANCE_FLYING" -> steps.add("MOVE " + x.gain.getOrDefault("sectorsReassigned", "?") + " sectors onto/off " + ac + " — level fleet utilization");
                case "REBALANCE_CREW" -> steps.add("RE-PAIR crew " + String.join("/", x.crew) + " — " + x.description);
                case "REPOSITION_OPTIMALLY" -> steps.add(!x.flights.isEmpty() && !x.advisory
                        ? "CANCEL repositioning " + fl + " — hold " + (ac.isEmpty() ? "the aircraft" : ac) + " at base and serve next-day demand directly"
                        : "RE-POSITION " + (ac.isEmpty() ? "aircraft" : ac) + " — " + x.description);
                case "COMPLETE_DISPATCH" -> steps.add("COMPLETE THE DISPATCH FILE for " + (fl.isEmpty() ? ac : fl) + " — permits, MVT and crew slots, in the Dispatch module");
                case "RESCHEDULE_MX" -> steps.add("BOOK MAINTENANCE SLOT for " + ac + " — " + x.description);
                case "GROUND_OR_RESCHEDULE" -> steps.add("CLEAR CAMO ITEM on " + ac + " — " + x.description);
                case "REROUTE_OR_DELAY" -> steps.add("RE-TIME around restriction — " + x.description);
                case "REDISTRIBUTE_PARKING" -> steps.add("CONFIRM STANDS — " + x.description);
                case "OPTIMIZE_CREW_POS" -> steps.add("CREW POSITIONING — " + x.description);
                default -> steps.add(x.action + " — " + x.description);
            }
        }
        return steps;
    }

    /* ═══════════════════ COST — Cost (l. 90679) ════════════════════════════ */

    private static final class Cost {
        final CostParamsDto p;

        Cost(CostParamsDto p) {
            this.p = p;
        }

        TypeRateDto rateFor(String type) {
            String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
            for (TypeRateDto rate : TYPE_RATES) {
                if (t.contains(rate.key())) {
                    return rate;
                }
            }
            return TYPE_RATES.get(4);
        }

        double perFH(String type) {
            TypeRateDto r = rateFor(type);
            return r.fuelGalPerHour() * p.fuelPrice() + r.mxPerFh() + r.enginePerFh() + r.crewPerFh();
        }

        PlanCost planCost(Scenario scn) {
            Plan plan = scn.plan;
            PlanCost c = new PlanCost();
            for (Flight f : plan.flights) {
                if ("cancelled".equals(f.status) || "GROUND".equals(f.kind)) {
                    continue;
                }
                TypeRateDto r = rateFor(f.type);
                double fh = Math.max(0, f.e - f.s);
                c.flying += fh * perFH(f.type);
                c.handling += r.handlingPerCycle();
                c.nav += plan.distNm(f.from, f.to) * r.navPerNm();
            }
            for (Flight f : plan.flights) {
                if (f.delayMin > 0) {
                    c.delays += f.delayMin * p.delayPerMin();
                }
            }
            c.cancellations = plan.flights.stream().filter(f -> "cancelled".equals(f.status)).count() * p.cxlPenalty();
            c.aog = plan.tails.stream().filter(t -> "aog".equals(t.baseStatus)).count() * p.aogPerDay();
            c.mxExposure = (plan.mxItems.stream().filter(m -> !m.resolved).count() + plan.camoItems.stream().filter(m -> !m.resolved).count()) * p.mxSlotMiss();
            for (Tail t : plan.tails) {
                for (int d = 0; d < plan.days.size(); d++) {
                    int day = d;
                    List<Flight> fs = plan.flights.stream().filter(f -> f.reg.equals(t.reg) && f.day == day && !"cancelled".equals(f.status) && !"GROUND".equals(f.kind))
                            .sorted(Comparator.comparingDouble(f -> f.s)).toList();
                    if (fs.isEmpty()) {
                        continue;
                    }
                    String eod = fs.get(fs.size() - 1).to;
                    if (!eod.equals(plan.baseApt) && !eod.equals(DEFAULT_BASE) && !eod.equals(t.base)) {
                        c.night += p.nightStop();
                    }
                }
            }
            for (Request r : plan.requests) {
                if (r.servedBy == null) {
                    c.lostRevenue += plan.blockH(r.from, r.to) * p.charterYield();
                }
            }
            c.crewDisruption = scn.anomalies.stream().filter(a -> !a.resolved && ("CREW_CONFLICT".equals(a.type) || "CREW_REPOSITIONING".equals(a.type))).count() * p.crewDisrupt();
            c.total = c.flying + c.handling + c.nav + c.night + c.delays + c.cancellations + c.aog + c.mxExposure + c.lostRevenue + c.crewDisruption;
            return c;
        }

        int actionValue(Action x, Scenario scn) {
            String ty = x.aircraft.isEmpty() ? null : scn.plan.tail(x.aircraft.get(0)) == null ? null : scn.plan.tail(x.aircraft.get(0)).type;
            TypeRateDto r = rateFor(ty);
            double fh = perFH(ty);
            double usd = 0;
            usd += num(x.gain.get("ferryHrsSaved")) * fh;
            usd += num(x.gain.get("blockHrsSaved")) * fh;
            usd += num(x.gain.get("nmAvoided")) * r.navPerNm();
            if ("REMOVE_FERRY".equals(x.action) || "REPOSITION_OPTIMALLY".equals(x.action)) {
                usd += x.flights.size() * r.handlingPerCycle();
            }
            usd += num(x.gain.get("delayRecoveredMin")) * p.delayPerMin();
            usd += num(x.gain.get("critConflictsCleared")) * p.crewDisrupt();
            usd += num(x.gain.get("deadheadsAvoided")) * p.crewDeadhead();
            usd += num(x.gain.get("camoConflictsCleared")) * p.mxSlotMiss();
            if (x.gain.containsKey("fhProtected")) {
                usd += p.mxSlotMiss();
            }
            usd += num(x.gain.get("standsFreed")) * p.nightStop();
            usd += num(x.gain.get("demandsRecovered")) * p.cxlPenalty();
            usd += num(x.gain.get("demandsServed")) * 2.0 * p.charterYield();
            if (num(x.gain.get("flightsMerged")) > 1) {
                usd += (num(x.gain.get("flightsMerged")) - 1) * 1.5 * fh;
            }
            return (int) Math.round(usd);
        }

        /** {@code estValue} (l. 90945) — la valeur d'une inefficience avant toute action. */
        int estValue(Anomaly a, Scenario scn) {
            Plan plan = scn.plan;
            String ty = a.reg == null || plan.tail(a.reg) == null ? null : plan.tail(a.reg).type;
            double fhRate = perFH(ty);
            TypeRateDto r = rateFor(ty);
            List<Flight> legs = a.flights.stream().map(plan::byId).filter(f -> f != null).toList();
            double fh = legs.stream().mapToDouble(f -> Math.max(0, f.e - f.s)).sum();
            double nm = legs.stream().mapToDouble(f -> plan.distNm(f.from, f.to)).sum();
            double v = switch (a.type) {
                case "FERRY_UNNECESSARY", "REPOSITIONING_UNNECESSARY", "REPOSITIONING_AFTER_CXL", "MISPOSITIONED", "FAR_FROM_DEMAND", "SAME_APT_CLUSTER" ->
                        fh * fhRate + nm * r.navPerNm() + legs.size() * r.handlingPerCycle();
                case "ROTATION_INEFFICIENT" -> Math.max(0, nm - plan.distNm(legs.isEmpty() ? plan.baseApt : legs.get(0).from,
                        a.origTo == null ? plan.baseApt : a.origTo)) * r.navPerNm() + 1.2 * fhRate;
                case "DELAY" -> (legs.isEmpty() || legs.get(0).delayMin == 0 ? 45 : legs.get(0).delayMin) * p.delayPerMin();
                case "CANCELLATION" -> p.cxlPenalty();
                case "AOG" -> p.aogPerDay();
                case "CREW_CONFLICT" -> p.crewDisrupt();
                case "CREW_REPOSITIONING" -> p.crewDeadhead();
                case "MX_CRITICAL", "CAMO_CONFLICT" -> p.mxSlotMiss();
                case "VIP_REQUEST", "COMBINABLE_FLIGHTS" -> 2 * p.charterYield();
                case "EMPTY_LEG_OPPORTUNITY" -> fh * fhRate * 0.5;
                case "PARKING_LIMIT" -> Math.max(1, a.tails.size()) * p.nightStop();
                case "DORMANT_AC", "UNDERUTILIZED_AC", "OVERUTILIZED_AC" -> 2 * fhRate;
                default -> 1200;
            };
            return (int) Math.round(v);
        }

        private static double num(Object v) {
            return v instanceof Number n ? n.doubleValue() : 0;
        }
    }

    private static final class PlanCost {
        double flying;
        double handling;
        double nav;
        double night;
        double delays;
        double cancellations;
        double aog;
        double mxExposure;
        double lostRevenue;
        double crewDisruption;
        double total;

        PlanCostDto toDto() {
            return new PlanCostDto(Math.round(flying), Math.round(handling), Math.round(nav), Math.round(night), Math.round(delays),
                    Math.round(cancellations), Math.round(aog), Math.round(mxExposure), Math.round(lostRevenue), Math.round(crewDisruption), Math.round(total));
        }
    }

    /* ═══════════════════ model ═════════════════════════════════════════════ */

    private static final class Plan {
        final List<String> days = new ArrayList<>();
        final List<Tail> tails = new ArrayList<>();
        final List<Flight> flights = new ArrayList<>();
        final Set<String> crews = new LinkedHashSet<>();
        final List<Request> requests = new ArrayList<>();
        final List<MxItem> mxItems = new ArrayList<>();
        final List<CamoItem> camoItems = new ArrayList<>();
        Map<String, AirportDto> airports = Map.of();
        String baseApt = DEFAULT_BASE;
        double[] window = {0, 24};

        Flight byId(String id) {
            for (Flight f : flights) {
                if (f.id.equals(id)) {
                    return f;
                }
            }
            return null;
        }

        Tail tail(String reg) {
            for (Tail t : tails) {
                if (t.reg.equals(reg)) {
                    return t;
                }
            }
            return null;
        }

        /** {@code DATA.distNm} — haversine, R = 3440.065 NM. Zero quand une coordonnee manque. */
        double distNm(String a, String b) {
            AirportDto x = airports.get(a);
            AirportDto y = airports.get(b);
            if (x == null || y == null || x.latitude() == null || y.latitude() == null || x.longitude() == null || y.longitude() == null) {
                return 0;
            }
            double r = 3440.065;
            double la1 = Math.toRadians(x.latitude().doubleValue());
            double la2 = Math.toRadians(y.latitude().doubleValue());
            double dLa = la2 - la1;
            double dLo = Math.toRadians(y.longitude().doubleValue() - x.longitude().doubleValue());
            double h = Math.sin(dLa / 2) * Math.sin(dLa / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
            return 2 * r * Math.asin(Math.sqrt(h));
        }

        double blockH(String from, String to) {
            return round2(distNm(from, to) / CRUISE_KTS + TAXI_H);
        }

        Plan copy() {
            Plan c = new Plan();
            c.days.addAll(days);
            c.tails.addAll(tails);
            c.crews.addAll(crews);
            c.airports = airports;
            c.baseApt = baseApt;
            c.window = window;
            for (Flight f : flights) {
                c.flights.add(f.copy());
            }
            for (Request r : requests) {
                c.requests.add(r.copy());
            }
            for (MxItem m : mxItems) {
                c.mxItems.add(m.copy());
            }
            for (CamoItem m : camoItems) {
                c.camoItems.add(m.copy());
            }
            return c;
        }
    }

    private static final class Tail {
        UUID aircraftId;
        String reg;
        String type;
        String family;
        String baseStatus;
        String base;
        OffsetDateTime nextCheckDueAt;
        String nextCheckLabel;
    }

    private static final class Flight {
        String id;
        UUID legId;
        int day;
        String date;
        String reg;
        String type;
        String fn;
        String from;
        String to;
        double s;
        double e;
        OffsetDateTime std;
        OffsetDateTime sta;
        String status;
        String kind;
        int pax;
        int delayMin;
        String bookingRef;
        String broker;
        String paxGroup;
        String label;
        List<String> crew = new ArrayList<>();
        String crewFtl;
        String crewDocs;
        boolean crewComplete = true;
        final List<String> dispatchMissing = new ArrayList<>();
        final List<String> anomalyIds = new ArrayList<>();
        String movedFrom;
        boolean createdByEngine;

        Flight copy() {
            Flight c = new Flight();
            c.id = id;
            c.legId = legId;
            c.day = day;
            c.date = date;
            c.reg = reg;
            c.type = type;
            c.fn = fn;
            c.from = from;
            c.to = to;
            c.s = s;
            c.e = e;
            c.std = std;
            c.sta = sta;
            c.status = status;
            c.kind = kind;
            c.pax = pax;
            c.delayMin = delayMin;
            c.bookingRef = bookingRef;
            c.broker = broker;
            c.paxGroup = paxGroup;
            c.label = label;
            c.crew = new ArrayList<>(crew);
            c.crewFtl = crewFtl;
            c.crewDocs = crewDocs;
            c.crewComplete = crewComplete;
            c.dispatchMissing.addAll(dispatchMissing);
            c.anomalyIds.addAll(anomalyIds);
            return c;
        }
    }

    private static final class Request {
        String id;
        String kind;
        String from;
        String to;
        int day;
        double[] window;
        int pax;
        String anomalyId;
        String servedBy;
        String groundedReg;
        String fn;

        Request copy() {
            Request c = new Request();
            c.id = id;
            c.kind = kind;
            c.from = from;
            c.to = to;
            c.day = day;
            c.window = window.clone();
            c.pax = pax;
            c.anomalyId = anomalyId;
            c.servedBy = servedBy;
            c.groundedReg = groundedReg;
            c.fn = fn;
            return c;
        }
    }

    private static final class MxItem {
        String reg;
        String kind;
        OffsetDateTime dueAt;
        String note;
        String anomalyId;
        boolean resolved;

        MxItem copy() {
            MxItem c = new MxItem();
            c.reg = reg;
            c.kind = kind;
            c.dueAt = dueAt;
            c.note = note;
            c.anomalyId = anomalyId;
            c.resolved = resolved;
            return c;
        }
    }

    private static final class CamoItem {
        String reg;
        String item;
        String anomalyId;
        boolean resolved;

        CamoItem copy() {
            CamoItem c = new CamoItem();
            c.reg = reg;
            c.item = item;
            c.anomalyId = anomalyId;
            c.resolved = resolved;
            return c;
        }
    }

    private static final class Anomaly {
        String id;
        String type;
        String label;
        String severity;
        String expectedFix;
        String reg;
        int day;
        List<String> flights = List.of();
        List<String> airports = List.of();
        List<String> crew = List.of();
        List<String> tails = List.of();
        String airport;
        String via;
        String origTo;
        String item;
        String subtype;
        double plannedHrs;
        boolean ferryOnly;
        String note;
        boolean outOfScope;
        boolean resolved;
        int estValue;

        Anomaly copy() {
            Anomaly c = new Anomaly();
            c.id = id;
            c.type = type;
            c.label = label;
            c.severity = severity;
            c.expectedFix = expectedFix;
            c.reg = reg;
            c.day = day;
            c.flights = flights;
            c.airports = airports;
            c.crew = crew;
            c.tails = tails;
            c.airport = airport;
            c.via = via;
            c.origTo = origTo;
            c.item = item;
            c.subtype = subtype;
            c.plannedHrs = plannedHrs;
            c.ferryOnly = ferryOnly;
            c.note = note;
            c.outOfScope = outOfScope;
            c.resolved = resolved;
            c.estValue = estValue;
            return c;
        }
    }

    private static final class Action {
        String anomalyId;
        String type;
        String label;
        String action;
        String severity;
        List<String> flights = List.of();
        List<String> aircraft = List.of();
        List<String> crew = List.of();
        Map<String, Object> gain = new LinkedHashMap<>();
        boolean advisory;
        int valueUsd;
        String description;
        String step;
    }

    private static final class Operation {
        String id;
        String op;
        String date;
        String fn;
        String reg;
        UUID legId;
        String fromReg;
        String toReg;
        OffsetDateTime newStd;
        OffsetDateTime newSta;
        OffsetDateTime oldStd;
        OffsetDateTime oldSta;
        boolean applicable;
        boolean ferry;
        String title;
        String brief;
        String text;
        int valueUsd;
        String anomalyId;
    }

    private record FollowUp(String id, String module, String brief, String text) {
    }

    private static final class Scenario {
        final String id;
        final String name;
        final Plan plan;
        final List<Anomaly> anomalies = new ArrayList<>();
        int exclusiveUseKept;
        final List<String> exclusiveUseDetail = new ArrayList<>();

        Scenario(Plan plan, String name) {
            this.id = "LIVE-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
            this.name = name;
            this.plan = plan;
        }

        private Scenario(String id, String name, Plan plan) {
            this.id = id;
            this.name = name;
            this.plan = plan;
        }

        Anomaly add(String type, String reg, int day, List<String> flights, String note) {
            AnomalyType meta = AnomalyType.valueOf(type);
            Anomaly a = new Anomaly();
            a.id = id + "-D-" + String.format("%03d", anomalies.size() + 1);
            a.type = type;
            a.label = meta.label();
            a.severity = switch (meta.severity()) {
                case CRITICAL -> "crit";
                case HIGH -> "high";
                case MEDIUM -> "med";
                case LOW -> "low";
            };
            a.expectedFix = meta.expectedFix();
            a.reg = reg;
            a.day = day;
            a.flights = List.copyOf(flights);
            a.note = note;
            anomalies.add(a);
            return a;
        }

        Anomaly byId(String anomalyId) {
            for (Anomaly a : anomalies) {
                if (a.id.equals(anomalyId)) {
                    return a;
                }
            }
            return null;
        }

        Scenario copy() {
            Scenario c = new Scenario(id, name, plan.copy());
            for (Anomaly a : anomalies) {
                c.anomalies.add(a.copy());
            }
            c.exclusiveUseKept = exclusiveUseKept;
            c.exclusiveUseDetail.addAll(exclusiveUseDetail);
            return c;
        }
    }

    /* ═══════════════════ helpers ═══════════════════════════════════════════ */

    private OptAnomalyDto toDto(Anomaly a) {
        return new OptAnomalyDto(a.id, a.type, a.label, a.severity, a.expectedFix, SCOPE_OF_TYPE.getOrDefault(a.type, "—"), !a.outOfScope,
                a.reg, List.of(), List.of(), a.note, a.estValue);
    }

    private OptActionDto toDto(Action x) {
        return new OptActionDto(x.anomalyId, x.type, x.label, x.action, x.severity, x.flights, x.aircraft, x.crew, x.gain, x.advisory, x.valueUsd, x.description, x.step);
    }

    private OptOperationDto toDto(Operation o) {
        return new OptOperationDto(o.id, o.op, o.date, o.fn, o.reg, o.legId, o.fromReg, o.toReg, o.newStd, o.newSta, o.oldStd, o.oldSta,
                o.applicable, o.ferry, o.title, o.brief, o.text, o.valueUsd, o.anomalyId);
    }

    /** {@code fleetFamily} — FALCON / CITATION / LEGACY / E190 / OTHER, sur le modele. */
    private static String fleetFamily(String type) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (t.contains("FALCON")) {
            return "FALCON";
        }
        if (t.contains("CITATION") || t.matches(".*\\bCJ\\d.*") || t.contains("M2")) {
            return "CITATION";
        }
        if (t.contains("LEGACY")) {
            return "LEGACY";
        }
        if (t.contains("LINEAGE") || t.contains("E190") || t.contains("190")) {
            return "E190";
        }
        return "OTHER";
    }

    private static double hours(OffsetDateTime at, LocalDate day) {
        return Duration.between(day.atStartOfDay().atOffset(ZoneOffset.UTC), at).toMinutes() / 60.0;
    }

    private static String hhmm(double dec) {
        if (dec >= 24 && dec < 24.0001) {
            return "24:00";
        }
        double d = ((dec % 24) + 24) % 24;
        int h = (int) Math.floor(d);
        int m = (int) Math.round((d - h) * 60);
        if (m == 60) {
            h = (h + 1) % 24;
            m = 0;
        }
        return String.format("%02d:%02d", h, m);
    }

    private static String dayLabel(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            String[] mon = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return String.format("%02d %s", d.getDayOfMonth(), mon[d.getMonthValue() - 1]);
        } catch (Exception ex) {
            return date == null ? "" : date;
        }
    }

    private static String legRef(Flight f) {
        String dow = "";
        try {
            dow = LocalDate.parse(f.date).getDayOfWeek().toString().substring(0, 3);
            dow = dow.charAt(0) + dow.substring(1).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            // pas de date lisible : la reference se passe du jour de semaine
        }
        return f.fn + " " + f.from + "→" + f.to + "  " + hhmm(f.s) + "–" + hhmm(f.e) + " UTC  " + dow + " " + f.date;
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
