package com.thenetworkplan.networkplan.dispatch.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.config.AsyncConfig;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchBoardDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchKpiDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchRowDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchRowKind;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchTab;
import com.thenetworkplan.networkplan.dispatch.service.DispatchBoardService;
import com.thenetworkplan.networkplan.flightfollowing.service.LegRiskAssessor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.ops.service.OnTimePerformanceRule;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Assembles the dispatch board.
 *
 * <h2>Why the fan-out</h2>
 * The programme is one query. The five things the board says about each leg —
 * ground services, permits, crew, the tails that cannot fly, the open MEL items
 * — are five independent reads in five different schemas. Run in sequence they
 * add up; run on virtual threads they cost roughly the slowest one, and because
 * a virtual thread parked on a blocking JDBC call holds no platform thread,
 * the concurrency costs almost nothing.
 *
 * <p>The tenant is passed into every task rather than read from a thread-local:
 * {@code TenantContext} belongs to the request thread and is deliberately empty
 * on the threads spawned here.
 *
 * <h2>Why it is cached</h2>
 * An OCC leaves this screen open and it polls. Twenty seconds of L1/L2 cache
 * turns a room full of dispatchers into one query set, and every write path in
 * the application evicts it, so a new AOG or a confirmed permit shows up on the
 * next refresh rather than twenty seconds later.
 */
@Service
public class DispatchBoardServiceImpl implements DispatchBoardService {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchBoardServiceImpl.class);

    /** Only used to render a leg that has no assignment at all. */
    private static final int DEFAULT_MINIMUM_FLIGHT_DECK = 2;

    private final LegService legService;
    private final GroundServiceService groundServiceService;
    private final PermitService permitService;
    private final CrewAssignmentService crewAssignmentService;
    private final AircraftService aircraftService;
    private final AirportService airportService;
    private final OpsProperties opsProperties;
    private final OnTimePerformanceRule onTimePerformanceRule;
    private final LegRiskAssessor legRiskAssessor;
    private final ExecutorService executor;

    public DispatchBoardServiceImpl(LegService legService,
                                    LegRiskAssessor legRiskAssessor,
                                    GroundServiceService groundServiceService,
                                    PermitService permitService,
                                    CrewAssignmentService crewAssignmentService,
                                    AircraftService aircraftService,
                                    AirportService airportService,
                                    OpsProperties opsProperties,
                                    OnTimePerformanceRule onTimePerformanceRule,
                                    @Qualifier(AsyncConfig.DISPATCH_EXECUTOR) ExecutorService executor) {
        this.legService = legService;
        this.groundServiceService = groundServiceService;
        this.permitService = permitService;
        this.crewAssignmentService = crewAssignmentService;
        this.aircraftService = aircraftService;
        this.airportService = airportService;
        this.opsProperties = opsProperties;
        this.onTimePerformanceRule = onTimePerformanceRule;
        this.legRiskAssessor = legRiskAssessor;
        this.executor = executor;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.DISPATCH_BOARD, key = "#tenantId + ':' + #filter.cacheKey()")
    public DispatchBoardDto load(UUID tenantId, DispatchFilter filter) {
        long startedAt = System.nanoTime();

        List<LegDto> legs = legService.findProgramme(tenantId, filter.date());
        List<UUID> legIds = legs.stream().map(LegDto::id).toList();

        // Five independent reads, one virtual thread each.
        CompletableFuture<Map<UUID, LegServicesSummary>> services =
                async(() -> groundServiceService.summariseByLegIds(tenantId, legIds));
        CompletableFuture<Map<UUID, LegPermitsSummary>> permits =
                async(() -> permitService.summariseByLegIds(tenantId, legIds));
        CompletableFuture<Map<UUID, LegCrewDto>> crew =
                async(() -> crewAssignmentService.findByLegIds(tenantId, legIds, filter.date()));
        CompletableFuture<List<AircraftDto>> grounded =
                async(() -> aircraftService.findGrounded(tenantId));
        // The whole fleet, for the availability denominator. Cached five
        // minutes, so this costs nothing on a board refreshed every thirty
        // seconds — and it is the only honest denominator: the tails that flew
        // today do not include the ones that could not.
        CompletableFuture<List<AircraftDto>> fleet =
                async(() -> aircraftService.findFleet(tenantId));
        CompletableFuture<Map<UUID, List<MelItemDto>>> openMel =
                async(() -> aircraftService.findOpenMelByAircraft(tenantId));
        // Yesterday, for the comparison the OCC reads next to today's figures.
        // One more read on its own virtual thread: the board still costs the
        // slowest query, not the sum.
        CompletableFuture<List<LegDto>> yesterday =
                async(() -> legService.findProgramme(tenantId, filter.date().minusDays(1)));

        Map<UUID, LegServicesSummary> servicesByLeg = await(services);
        Map<UUID, LegPermitsSummary> permitsByLeg = await(permits);
        Map<UUID, LegCrewDto> crewByLeg = await(crew);
        List<AircraftDto> groundedAircraft = await(grounded);
        List<AircraftDto> wholeFleet = await(fleet);
        Map<UUID, List<MelItemDto>> melByAircraft = await(openMel);
        List<LegDto> yesterdayLegs = await(yesterday);

        Map<String, AirportDto> stations =
                airportService.findAllByIcao(stationCodes(legs, groundedAircraft));

        List<DispatchRowDto> groundRows = groundRows(groundedAircraft, melByAircraft, stations);
        List<DispatchRowDto> flightRows =
                flightRows(legs, servicesByLeg, permitsByLeg, crewByLeg, melByAircraft, stations);

        List<DispatchRowDto> allRows = new ArrayList<>(groundRows.size() + flightRows.size());
        allRows.addAll(groundRows);
        allRows.addAll(flightRows);

        List<DispatchRowDto> selected = allRows.stream()
                .filter(row -> matchesSelectors(row, filter))
                .filter(row -> matchesTab(row, filter.tab()))
                .toList();

        DispatchKpiDto kpi = kpi(legs, servicesByLeg, crewByLeg, groundedAircraft,
                wholeFleet.size(), yesterdayLegs,
                permitService.countOutstanding(tenantId, legIds), allRows);

        DispatchBoardDto board = new DispatchBoardDto(
                filter.date(),
                kpi,
                selected,
                distinct(allRows, DispatchRowDto::icaoType),
                distinct(allRows, DispatchRowDto::baseIcao),
                tabCounts(allRows, filter),
                OffsetDateTime.now());

        LOG.debug("Dispatch board for {} assembled in {} ms ({} rows)",
                filter.date(), (System.nanoTime() - startedAt) / 1_000_000, selected.size());
        return board;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not cached: the board is read every thirty seconds by everyone, one
     * leg is read when somebody opens it. The three reads below are the same
     * ones the board makes, narrowed to a single identifier, and they go
     * through the same {@link #flightRows} so that the row cannot drift from
     * the row on the board.
     */
    @Override
    public DispatchRowDto findRow(UUID tenantId, UUID legId) {
        LegDto leg = legService.findById(tenantId, legId);
        List<UUID> legIds = List.of(leg.id());

        CompletableFuture<Map<UUID, LegServicesSummary>> services =
                async(() -> groundServiceService.summariseByLegIds(tenantId, legIds));
        CompletableFuture<Map<UUID, LegPermitsSummary>> permits =
                async(() -> permitService.summariseByLegIds(tenantId, legIds));
        CompletableFuture<Map<UUID, LegCrewDto>> crew = async(() ->
                crewAssignmentService.findByLegIds(tenantId, legIds, leg.std().toLocalDate()));

        Map<String, AirportDto> stations =
                airportService.findAllByIcao(stationCodes(List.of(leg), List.of()));

        return flightRows(List.of(leg), await(services), await(permits), await(crew),
                aircraftService.findOpenMelByAircraft(tenantId), stations).getFirst();
    }

    // ---------------------------------------------------------------- rows

    private List<DispatchRowDto> groundRows(List<AircraftDto> grounded,
                                            Map<UUID, List<MelItemDto>> melByAircraft,
                                            Map<String, AirportDto> stations) {
        List<DispatchRowDto> rows = new ArrayList<>(grounded.size());
        for (AircraftDto aircraft : grounded) {
            String station = aircraft.currentBaseIcao() != null
                    ? aircraft.currentBaseIcao() : aircraft.homeBaseIcao();
            String code = displayCode(stations, station);
            boolean aog = "AOG".equals(aircraft.status());
            List<MelItemDto> mel = melByAircraft.getOrDefault(aircraft.id(), List.of());
            boolean melBlocking = mel.stream().anyMatch(MelItemDto::blocksDispatch);

            rows.add(new DispatchRowDto(
                    DispatchRowKind.GROUND,
                    aircraft.id(),
                    null,
                    aircraft.id(),
                    null,
                    label(aircraft),
                    /* CRITICAL, comme l'annexe — et comme l'exploitant l'a
                       demande apres que la question lui a ete posee.
                       C'est un ETAT, pas une sortie de matrice. La matrice de
                       l'ICAO Doc 9859 score un VOL (gravite du resultat redoute
                       x probabilite) ; un appareil immobilise n'a pas de
                       resultat redoute, et l'y passer donnait MEDIUM 8 — a la
                       fois faux et rassurant sur la ligne la plus grave du
                       tableau. Un appareil non remis en service est le plus
                       haut niveau d'attention qu'une ligne puisse porter, et
                       c'est ce que le mot dit.

                       Ce qui NE suit PAS, volontairement : l'indice. Aucune
                       matrice n'a tourne, donc aucun chiffre n'est affiche a
                       cote. Un « INDEX 16 » ecrit ici serait un nombre que
                       personne ne peut refaire. */
                    "CRITICAL",
                    aircraft.registration(),
                    aircraft.icaoType(),
                    aircraft.model(),
                    station,
                    code,
                    station,
                    code,
                    code + " (ground)",
                    station,
                    null, null, null, null, null, null, null,
                    "ATTENTION", 0, 0,
                    0,
                    false, 0, 0, "UNKNOWN", "UNKNOWN",
                    aircraft.status(),
                    aog ? "AOG" : "MAINTENANCE",
                    true,
                    melBlocking,
                    0,
                    aircraft.statusReason(),
                    /* L'annexe ouvre sur un avion au sol EXACTEMENT le meme
                       dossier que sur un vol : sa ligne « sol » est un vol
                       fictif dont le depart et l'arrivee sont l'escale ou
                       l'appareil est immobilise. Le bandeau de route montre
                       donc ce terrain des deux cotes — ce qui est la verite :
                       l'avion y est, et il n'en part pas. */
                    name(stations, station),
                    city(stations, station),
                    country(stations, station),
                    name(stations, station),
                    city(stations, station),
                    country(stations, station),
                    /* Pas de type de vol : il n'y a pas de vol. La pastille du
                       bandeau porte a la place le motif d'immobilisation, comme
                       chez elle (FL.nature lit flight.label avant tout). */
                    null,
                    /* Ni nature commerciale ni lettre de case 8 : un appareil
                       immobilise ne depose pas de plan de vol. */
                    null, null, 0,
                    /* Pas d'indice : aucune matrice n'a tourne (voir le niveau
                       plus haut). Le facteur dominant et l'action sont en
                       revanche ceux que le dossier doit lire — ce qui bloque,
                       et ce que la CAMO en dit. Le bandeau a ainsi un seul
                       chemin de rendu, vol ou appareil au sol. */
                    0,
                    melBlocking ? "MEL blocks dispatch" : null,
                    aircraft.statusReason(),
                    null));
        }
        return rows;
    }

    private List<DispatchRowDto> flightRows(List<LegDto> legs,
                                            Map<UUID, LegServicesSummary> servicesByLeg,
                                            Map<UUID, LegPermitsSummary> permitsByLeg,
                                            Map<UUID, LegCrewDto> crewByLeg,
                                            Map<UUID, List<MelItemDto>> melByAircraft,
                                            Map<String, AirportDto> stations) {
        List<DispatchRowDto> rows = new ArrayList<>(legs.size());
        for (LegDto leg : legs) {
            LegServicesSummary services = servicesByLeg.getOrDefault(
                    leg.id(), LegServicesSummary.nothingRequested(leg.id()));
            LegPermitsSummary permits = permitsByLeg.getOrDefault(
                    leg.id(), LegPermitsSummary.none(leg.id()));
            LegCrewDto crew = crewByLeg.getOrDefault(
                    leg.id(), LegCrewDto.unassigned(leg.id(), DEFAULT_MINIMUM_FLIGHT_DECK));

            boolean melBlocking = melByAircraft.getOrDefault(leg.aircraftId(), List.of()).stream()
                    .anyMatch(MelItemDto::blocksDispatch);
            /* Le risque est CALCULE, pas relu. La colonne ops.legs.risk_level
               n'est ecrite par personne dans l'application : elle vient du jeu
               d'amorce. Le tableau la lisait pendant que Flight Following
               evaluait, et le meme vol pouvait donc porter deux niveaux selon
               l'ecran ouvert. Un exploitant tient UNE image du risque. */
            SmsRiskRule.Assessment risk = legRiskAssessor.assess(
                    melByAircraft.getOrDefault(leg.aircraftId(), List.of()).stream()
                            .findFirst().orElse(null),
                    crew);

            int delayMinutes = delayMinutes(leg);
            boolean attention = !"READY".equals(services.readiness())
                    || permits.outstanding() > 0
                    || !crew.complete()
                    || !"OK".equals(crew.ftlStatus())
                    || melBlocking
                    || !"SERVICEABLE".equals(leg.aircraftStatus());

            String depCode = displayCode(stations, leg.depIcao());
            String arrCode = displayCode(stations, leg.arrIcao());

            rows.add(new DispatchRowDto(
                    DispatchRowKind.FLIGHT,
                    leg.id(),
                    leg.id(),
                    leg.aircraftId(),
                    leg.flightNo(),
                    leg.flightNo(),
                    risk.level(),
                    leg.registration(),
                    leg.icaoType(),
                    leg.model(),
                    leg.depIcao(),
                    depCode,
                    leg.arrIcao(),
                    arrCode,
                    depCode + " → " + arrCode,
                    leg.baseIcao(),
                    leg.std(),
                    leg.etd(),
                    leg.outAt(),
                    leg.sta(),
                    leg.eta(),
                    leg.inAt(),
                    leg.ctot(),
                    services.readiness(),
                    services.confirmed(),
                    services.total(),
                    permits.outstanding(),
                    crew.complete(),
                    crew.seatsFilled(),
                    crew.minimumSeats(),
                    crew.ftlStatus(),
                    crew.documentStatus(),
                    leg.status(),
                    tone(leg, delayMinutes),
                    attention,
                    melBlocking,
                    delayMinutes,
                    leg.remark(),
                    // Les escales sont deja chargees pour le tableau : le
                    // dossier de vol les relit en memoire, pas en base.
                    name(stations, leg.depIcao()),
                    city(stations, leg.depIcao()),
                    country(stations, leg.depIcao()),
                    name(stations, leg.arrIcao()),
                    city(stations, leg.arrIcao()),
                    country(stations, leg.arrIcao()),
                    leg.flightType(),
                    leg.commercialType(),
                    leg.flightPlanLetter(),
                    leg.paxCount(),
                    risk.index(),
                    dominant(risk),
                    risk.action(),
                    leg.mvtSentAt()));
        }
        return rows;
    }

    /**
     * La phrase du facteur le plus grave — ce que l'annexe appelle « topLabel ».
     *
     * <p>Un bandeau qui annonce un indice sans dire ce qui le porte oblige a
     * ouvrir le dossier pour le savoir. Les facteurs muets ne comptent pas :
     * « meteo inconnue » n'est pas ce qui rend un vol critique.
     */
    private static String dominant(SmsRiskRule.Assessment risk) {
        return risk.factors().stream()
                .filter(scored -> scored.level().active())
                .max(Comparator.comparingInt(scored -> scored.level().severity()))
                .map(SmsRiskRule.Scored::detail)
                .orElse(null);
    }

    /** « Tunis Carthage », quand le registre le connait. */
    private static String name(Map<String, AirportDto> stations, String icao) {
        AirportDto airport = stations.get(icao);
        return airport == null ? null : airport.name();
    }

    /** « Tunis » — la ville desservie, quand le registre la porte. */
    private static String city(Map<String, AirportDto> stations, String icao) {
        AirportDto airport = stations.get(icao);
        return airport == null ? null : airport.city();
    }

    private static String country(Map<String, AirportDto> stations, String icao) {
        AirportDto airport = stations.get(icao);
        return airport == null ? null : airport.countryIso2();
    }

    // ---------------------------------------------------------------- KPI

    /**
     * On-time performance over the legs that actually departed.
     *
     * <p>Delegated to {@link OnTimePerformanceRule}: the Flight Timeline shows
     * the same figure, and it has to be the same figure. The rule excludes a
     * leg with no off-block time rather than counting it as on time — that is
     * how a prototype reaches a hundred per cent on a day nothing flew — and
     * it carries the sample size with the percentage, so a screen can say
     * "on two departures" instead of presenting 100 % as a season record.
     */
    private OnTimePerformanceRule.Measure onTimePerformance(List<LegDto> legs) {
        return onTimePerformanceRule.measure(legs.stream()
                .map(leg -> new OnTimePerformanceRule.Departure(leg.std(), leg.outAt()))
                .toList());
    }

    private DispatchKpiDto kpi(List<LegDto> legs,
                               Map<UUID, LegServicesSummary> servicesByLeg,
                               Map<UUID, LegCrewDto> crewByLeg,
                               List<AircraftDto> grounded,
                               int fleetSize,
                               List<LegDto> yesterdayLegs,
                               long permitsOutstanding,
                               List<DispatchRowDto> allRows) {
        int servicesReady = 0;
        int servicesPending = 0;
        int crewUnassigned = 0;
        int delayed = 0;
        Set<UUID> tails = new LinkedHashSet<>();

        for (LegDto leg : legs) {
            tails.add(leg.aircraftId());
            LegServicesSummary services = servicesByLeg.get(leg.id());
            if (services != null && "READY".equals(services.readiness())) {
                servicesReady++;
            } else {
                servicesPending++;
            }
            LegCrewDto crew = crewByLeg.get(leg.id());
            if (crew == null || !crew.complete()) {
                crewUnassigned++;
            }
            if (delayMinutes(leg) > 0) {
                delayed++;
            }
        }

        int aog = (int) grounded.stream().filter(a -> "AOG".equals(a.status())).count();
        int maintenance = (int) grounded.stream().filter(a -> "MAINTENANCE".equals(a.status())).count();
        int needsAction = (int) allRows.stream().filter(DispatchRowDto::attention).count();

        int delayedYesterday = (int) yesterdayLegs.stream().filter(leg -> delayMinutes(leg) > 0).count();
        OnTimePerformanceRule.Measure otpToday = onTimePerformance(legs);
        OnTimePerformanceRule.Measure otpYesterday = onTimePerformance(yesterdayLegs);

        return new DispatchKpiDto(
                legs.size(),
                tails.size(),
                fleetSize,
                yesterdayLegs.size(),
                delayedYesterday,
                otpToday.percent(),
                otpToday.sample(),
                otpYesterday.percent(),
                opsProperties.getOtpTargetPercent(),
                servicesReady,
                servicesPending,
                (int) permitsOutstanding,
                crewUnassigned,
                aog + delayed,
                aog,
                maintenance,
                delayed,
                needsAction);
    }

    // ---------------------------------------------------------------- filters

    private boolean matchesSelectors(DispatchRowDto row, DispatchFilter filter) {
        if (filter.fleetType() != null && !filter.fleetType().equals(row.icaoType())) {
            return false;
        }
        return filter.baseIcao() == null || filter.baseIcao().equals(row.baseIcao());
    }

    private boolean matchesTab(DispatchRowDto row, DispatchTab tab) {
        return switch (tab) {
            case ALL_FLIGHTS -> true;
            case NEEDS_ACTION -> row.attention();
            case SCHEDULED -> row.kind() == DispatchRowKind.FLIGHT
                    && (row.status().equals("PLANNED")
                        || row.status().equals("PREPARED")
                        || row.status().equals("RELEASED"));
            case EN_ROUTE -> row.kind() == DispatchRowKind.FLIGHT && row.status().equals("DEPARTED");
            case DELAYED -> row.kind() == DispatchRowKind.FLIGHT && row.delayMinutes() > 0;
            case AOG_MAINTENANCE -> row.kind() == DispatchRowKind.GROUND;
        };
    }

    private Map<String, Integer> tabCounts(List<DispatchRowDto> allRows, DispatchFilter filter) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DispatchTab tab : DispatchTab.values()) {
            int count = (int) allRows.stream()
                    .filter(row -> matchesSelectors(row, filter))
                    .filter(row -> matchesTab(row, tab))
                    .count();
            counts.put(tab.name(), count);
        }
        return counts;
    }

    // ---------------------------------------------------------------- helpers

    private int delayMinutes(LegDto leg) {
        if (leg.etd() == null || leg.std() == null) {
            return 0;
        }
        long minutes = Duration.between(leg.std(), leg.etd()).toMinutes();
        return minutes >= opsProperties.getDelayThreshold().toMinutes() ? (int) minutes : 0;
    }

    private String tone(LegDto leg, int delayMinutes) {
        if ("CANCELLED".equals(leg.status())) {
            return "CANCELLED";
        }
        if (!"SERVICEABLE".equals(leg.aircraftStatus())) {
            return "AOG".equals(leg.aircraftStatus()) ? "AOG" : "MAINTENANCE";
        }
        if (delayMinutes > 0) {
            return "DELAYED";
        }
        return switch (leg.status()) {
            case "DEPARTED" -> "ENROUTE";
            case "ARRIVED", "CLOSED" -> "CLOSED";
            default -> "SCHEDULED";
        };
    }

    private String label(AircraftDto aircraft) {
        if (!"AOG".equals(aircraft.status())) {
            return "Scheduled maintenance";
        }
        String reason = aircraft.statusReason();
        return reason != null && reason.toLowerCase().contains("awaiting parts")
                ? "AOG — awaiting parts"
                : "AOG";
    }

    private Set<String> stationCodes(List<LegDto> legs, List<AircraftDto> grounded) {
        Set<String> codes = new LinkedHashSet<>();
        for (LegDto leg : legs) {
            codes.add(leg.depIcao());
            codes.add(leg.arrIcao());
        }
        for (AircraftDto aircraft : grounded) {
            if (aircraft.currentBaseIcao() != null) {
                codes.add(aircraft.currentBaseIcao());
            }
            if (aircraft.homeBaseIcao() != null) {
                codes.add(aircraft.homeBaseIcao());
            }
        }
        return codes;
    }

    private String displayCode(Map<String, AirportDto> stations, String icao) {
        if (icao == null) {
            return null;
        }
        AirportDto airport = stations.get(icao);
        return airport == null ? icao : airport.displayCode();
    }

    private List<String> distinct(List<DispatchRowDto> rows,
                                  java.util.function.Function<DispatchRowDto, String> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    /** Unwraps the completion wrapper so the global handler sees the real cause. */
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Dispatch board task failed", cause);
        }
    }
}
