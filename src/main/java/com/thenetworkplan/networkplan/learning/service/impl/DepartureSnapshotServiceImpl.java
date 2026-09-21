package com.thenetworkplan.networkplan.learning.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.config.LearningProperties;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.flightfollowing.service.LegRiskAssessor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import com.thenetworkplan.networkplan.learning.domain.DepartureSnapshot;
import com.thenetworkplan.networkplan.learning.dto.DepartureSnapshotDto;
import com.thenetworkplan.networkplan.learning.repository.DepartureSnapshotRepository;
import com.thenetworkplan.networkplan.learning.service.DepartureSnapshotService;
import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.DelayRecordRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import com.thenetworkplan.networkplan.weather.domain.WeatherObservation;
import com.thenetworkplan.networkplan.weather.repository.WeatherObservationRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * La « photo a H-1 » — voir V63 et {@link DepartureSnapshotService}.
 *
 * <p>Tout ce qui entre dans la photo est lu <b>a l'instant de la photo</b>, par
 * les memes services que VIGIL et le suivi de vol (permis, services, equipage,
 * MEL, risque SMS) et par la table des METAR recus. Deux gardes point-in-time
 * font la valeur de la table :
 * <ul>
 *   <li>le retard de la jambe precedente n'entre que si l'avion etait pose
 *       (ON ou IN enregistre) avant l'instant de la photo — sinon
 *       {@code inboundKnown} vaut faux et la valeur est absente, pas nulle ;
 *   <li>le METAR retenu est le dernier <i>observe</i> avant l'instant, et il
 *       est ecarte au-dela de {@code weatherStaleAfter}.
 * </ul>
 * Le resultat (OUT reel, retard, cible, code) est ajoute plus tard par
 * {@link #settleOutcomes} ; aucune colonne de la photo n'est retouchee.
 */
@Service
public class DepartureSnapshotServiceImpl implements DepartureSnapshotService {

    private final LegRepository legRepository;
    private final DepartureSnapshotRepository snapshots;
    private final DelayRecordRepository delayRecords;
    private final WeatherObservationRepository weather;
    private final PermitService permitService;
    private final GroundServiceService groundServiceService;
    private final CrewAssignmentService crewAssignmentService;
    private final AircraftService aircraftService;
    private final LegRiskAssessor legRiskAssessor;
    private final LearningProperties learning;
    private final OpsProperties ops;

    public DepartureSnapshotServiceImpl(LegRepository legRepository,
                                        DepartureSnapshotRepository snapshots,
                                        DelayRecordRepository delayRecords,
                                        WeatherObservationRepository weather,
                                        PermitService permitService,
                                        GroundServiceService groundServiceService,
                                        CrewAssignmentService crewAssignmentService,
                                        AircraftService aircraftService,
                                        LegRiskAssessor legRiskAssessor,
                                        LearningProperties learning,
                                        OpsProperties ops) {
        this.legRepository = legRepository;
        this.snapshots = snapshots;
        this.delayRecords = delayRecords;
        this.weather = weather;
        this.permitService = permitService;
        this.groundServiceService = groundServiceService;
        this.crewAssignmentService = crewAssignmentService;
        this.aircraftService = aircraftService;
        this.legRiskAssessor = legRiskAssessor;
        this.learning = learning;
        this.ops = ops;
    }

    /* ═══════════════════════════ la prise de la photo ═══════════════════════ */

    @Override
    @Transactional
    public int takeDue(UUID tenantId, OffsetDateTime now) {
        OffsetDateTime windowEnd = now.plus(learning.getLead());
        OffsetDateTime windowStart = windowEnd.minus(learning.getWindow());

        // Le programme autour de la fenetre : la veille pour la jambe precedente,
        // le lendemain pour la congestion a l'arrivee.
        List<Leg> programme = legRepository.findProgramme(tenantId,
                windowStart.minusHours(24), windowEnd.plusHours(24));

        List<Leg> due = programme.stream()
                .filter(leg -> leg.getStd().isAfter(windowStart) && !leg.getStd().isAfter(windowEnd))
                .filter(leg -> leg.getStatus() != LegStatus.CANCELLED && leg.getStatus() != LegStatus.CLOSED)
                .filter(leg -> !snapshots.existsByTenantIdAndLegId(tenantId, leg.getId()))
                .toList();
        if (due.isEmpty()) {
            return 0;
        }

        List<UUID> dueIds = due.stream().map(Leg::getId).toList();
        Map<UUID, LegPermitsSummary> permits = orEmpty(permitService.summariseByLegIds(tenantId, dueIds));
        Map<UUID, LegServicesSummary> services = orEmpty(groundServiceService.summariseByLegIds(tenantId, dueIds));
        Map<UUID, LegCrewDto> crew = orEmpty(crewAssignmentService.findByLegIds(tenantId, dueIds,
                due.get(0).getStd().toLocalDate()));
        Map<UUID, List<MelItemDto>> mel = orEmpty(aircraftService.findOpenMelByAircraft(tenantId));

        List<String> stations = due.stream()
                .flatMap(leg -> java.util.stream.Stream.of(leg.getDepIcao(), leg.getArrIcao()))
                .distinct().toList();
        Map<String, WeatherObservation> metarByStation = latestMetarBefore(stations, now);

        Map<UUID, List<Leg>> byAircraft = programme.stream()
                .filter(leg -> leg.getStatus() != LegStatus.CANCELLED)
                .sorted(Comparator.comparing(Leg::getStd))
                .collect(Collectors.groupingBy(leg -> leg.getAircraft().getId()));

        int taken = 0;
        for (Leg leg : due) {
            DepartureSnapshot s = new DepartureSnapshot();
            s.setTenantId(tenantId);
            s.setSource(Source.of("engine", "departure-snapshot"));
            s.setLegId(leg.getId());
            s.setTakenAt(now);
            s.setLeadMinutes((int) Duration.between(now, leg.getStd()).toMinutes());
            s.setFlightNo(leg.getFlightNo());
            s.setRegistration(leg.getAircraft().getRegistration());
            s.setIcaoType(leg.getAircraft().getAircraftType() == null ? null
                    : leg.getAircraft().getAircraftType().getIcaoType());
            s.setDepIcao(leg.getDepIcao());
            s.setArrIcao(leg.getArrIcao());
            s.setStd(leg.getStd());
            s.setSta(leg.getSta());

            rotation(s, leg, byAircraft.getOrDefault(leg.getAircraft().getId(), List.of()), now);
            congestion(s, leg, programme);
            facts(s, leg, permits.get(leg.getId()), services.get(leg.getId()), crew.get(leg.getId()),
                    mel.getOrDefault(leg.getAircraft().getId(), List.of()));
            weatherAt(s, metarByStation.get(leg.getDepIcao()), metarByStation.get(leg.getArrIcao()), now);

            snapshots.save(s);
            taken++;
        }
        return taken;
    }

    /** Rang du jour, escale programmee, et retard entrant — seulement si l'avion etait pose. */
    private static void rotation(DepartureSnapshot s, Leg leg, List<Leg> rotation, OffsetDateTime now) {
        LocalDate day = leg.getStd().toLocalDate();
        int index = 0;
        Leg previous = null;
        for (Leg other : rotation) {
            if (other.getStd().toLocalDate().equals(day) && !other.getStd().isAfter(leg.getStd())) {
                index++;
            }
            if (other.getStd().isBefore(leg.getStd()) && !other.getId().equals(leg.getId())) {
                previous = other;
            }
        }
        s.setLegIndex(index == 0 ? 1 : index);
        if (previous == null || previous.getSta() == null) {
            s.setInboundKnown(false);
            return;
        }
        s.setSchedTurnaroundMin((int) Duration.between(previous.getSta(), leg.getStd()).toMinutes());
        OffsetDateTime landed = previous.getOnAt() != null ? previous.getOnAt() : previous.getInAt();
        if (landed != null && !landed.isAfter(now)) {
            s.setInboundKnown(true);
            s.setInboundDelayMin((int) Duration.between(previous.getSta(), landed).toMinutes());
        } else {
            s.setInboundKnown(false);
        }
    }

    /** Mouvements programmes a +/- 30 min sur le meme terrain — l'horaire, jamais le reel. */
    private void congestion(DepartureSnapshot s, Leg leg, List<Leg> programme) {
        Duration margin = learning.getCongestionWindow();
        int dep = 0;
        int arr = 0;
        for (Leg other : programme) {
            if (other.getId().equals(leg.getId()) || other.getStatus() == LegStatus.CANCELLED) {
                continue;
            }
            if (leg.getDepIcao().equals(other.getDepIcao())
                    && Duration.between(leg.getStd(), other.getStd()).abs().compareTo(margin) <= 0) {
                dep++;
            }
            if (leg.getArrIcao().equals(other.getArrIcao()) && other.getSta() != null && leg.getSta() != null
                    && Duration.between(leg.getSta(), other.getSta()).abs().compareTo(margin) <= 0) {
                arr++;
            }
        }
        s.setDepCongestion(dep);
        s.setArrCongestion(arr);
    }

    /** Les faits que VIGIL lit, tels qu'ils etaient a l'instant de la photo. */
    private void facts(DepartureSnapshot s, Leg leg, LegPermitsSummary permits, LegServicesSummary services,
                       LegCrewDto crew, List<MelItemDto> mel) {
        if (permits != null) {
            s.setPermitsTotal(permits.total());
            s.setPermitsOutstanding(permits.outstanding());
        }
        if (services != null) {
            s.setServicesTotal(services.total());
            s.setServicesConfirmed(services.confirmed());
            s.setServicesReadiness(services.readiness());
        }
        if (crew != null) {
            s.setCrewComplete(crew.complete());
            s.setCrewFtlStatus(crew.ftlStatus());
            s.setCrewDocumentStatus(crew.documentStatus());
        }
        s.setMelOpen(mel.size());
        s.setMelBlocking(mel.stream().anyMatch(MelItemDto::blocksDispatch));
        SmsRiskRule.Assessment risk = legRiskAssessor.assess(worstMel(mel), crew);
        if (risk != null) {
            s.setRiskLevel(risk.level());
            s.setRiskIndex(risk.index());
        }
    }

    private static MelItemDto worstMel(List<MelItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream().filter(MelItemDto::blocksDispatch).findFirst().orElse(items.get(0));
    }

    /** Le dernier METAR observe avant {@code now}, par station, ou rien s'il est perime. */
    private Map<String, WeatherObservation> latestMetarBefore(List<String> stations, OffsetDateTime now) {
        Map<String, WeatherObservation> out = new HashMap<>();
        if (stations.isEmpty()) {
            return out;
        }
        OffsetDateTime since = now.minus(learning.getWeatherStaleAfter());
        List<WeatherObservation> recent = weather.findRecent(stations, since);
        if (recent == null) {
            return out;
        }
        for (WeatherObservation o : recent) {
            if (o.getObservedAt() == null || o.getObservedAt().isAfter(now) || o.getObservedAt().isBefore(since)) {
                continue;
            }
            WeatherObservation kept = out.get(o.getStationIcao());
            if (kept == null || o.getObservedAt().isAfter(kept.getObservedAt())) {
                out.put(o.getStationIcao(), o);
            }
        }
        return out;
    }

    private static void weatherAt(DepartureSnapshot s, WeatherObservation dep, WeatherObservation arr,
                                  OffsetDateTime now) {
        if (dep != null) {
            s.setDepWxObservedAt(dep.getObservedAt());
            s.setDepWxAgeMin((int) Duration.between(dep.getObservedAt(), now).toMinutes());
            s.setDepWindDirDeg(dep.getWindDirDeg());
            s.setDepWindKt(dep.getWindSpeedKt());
            s.setDepWindGustKt(dep.getWindGustKt());
            s.setDepVisibilityM(dep.getVisibilityM());
            s.setDepCeilingFt(dep.getCeilingFt());
            s.setDepCavok(dep.isCavok());
            s.setDepConditions(dep.getConditions());
            s.setDepFlightCategory(dep.getFlightCategory());
            s.setDepWxRaw(dep.getRawText());
        }
        if (arr != null) {
            s.setArrWxObservedAt(arr.getObservedAt());
            s.setArrWxAgeMin((int) Duration.between(arr.getObservedAt(), now).toMinutes());
            s.setArrWindDirDeg(arr.getWindDirDeg());
            s.setArrWindKt(arr.getWindSpeedKt());
            s.setArrWindGustKt(arr.getWindGustKt());
            s.setArrVisibilityM(arr.getVisibilityM());
            s.setArrCeilingFt(arr.getCeilingFt());
            s.setArrCavok(arr.isCavok());
            s.setArrConditions(arr.getConditions());
            s.setArrFlightCategory(arr.getFlightCategory());
            s.setArrWxRaw(arr.getRawText());
        }
    }

    /* ═══════════════════════════ le resultat ════════════════════════════════ */

    @Override
    @Transactional
    public int settleOutcomes(UUID tenantId, OffsetDateTime now) {
        List<DepartureSnapshot> open = snapshots.findByTenantIdAndOutcomeAtIsNull(tenantId);
        if (open.isEmpty()) {
            return 0;
        }
        List<UUID> legIds = open.stream().map(DepartureSnapshot::getLegId).toList();
        Map<UUID, Leg> legs = new HashMap<>();
        for (Leg leg : legRepository.findAllById(legIds)) {
            legs.put(leg.getId(), leg);
        }
        Map<UUID, DelayRecord> latestCode = new HashMap<>();
        for (DelayRecord record : delayRecords.findByTenantIdAndLegIdIn(tenantId, legIds)) {
            DelayRecord kept = latestCode.get(record.getLegId());
            if (kept == null || after(record.getCreatedAt(), kept.getCreatedAt())) {
                latestCode.put(record.getLegId(), record);
            }
        }

        long thresholdMin = ops.getDelayThreshold().toMinutes();
        int settled = 0;
        List<DepartureSnapshot> changed = new ArrayList<>();
        for (DepartureSnapshot s : open) {
            Leg leg = legs.get(s.getLegId());
            if (leg == null) {
                continue;
            }
            if (leg.getStatus() == LegStatus.CANCELLED) {
                s.setCancelled(true);
                s.setOutcomeAt(now);
                changed.add(s);
                settled++;
                continue;
            }
            if (leg.getOutAt() == null) {
                continue; // pas encore parti : la photo attend
            }
            int delay = (int) Duration.between(s.getStd(), leg.getOutAt()).toMinutes();
            s.setOutAt(leg.getOutAt());
            s.setDepDelayMin(delay);
            s.setTargetDelay15(delay >= thresholdMin);
            DelayRecord code = latestCode.get(leg.getId());
            s.setDelayCode(code == null ? null : code.getCode());
            s.setOutcomeAt(now);
            changed.add(s);
            settled++;
        }
        if (!changed.isEmpty()) {
            snapshots.saveAll(changed);
        }
        return settled;
    }

    private static boolean after(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) {
            return false;
        }
        return b == null || a.isAfter(b);
    }

    /* ═══════════════════════════ l'export ═══════════════════════════════════ */

    @Override
    @Transactional(readOnly = true)
    public List<DepartureSnapshotDto> find(UUID tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.atStartOfDay().atOffset(ZoneOffset.UTC);
        return snapshots.findByTenantIdAndStdGreaterThanEqualAndStdLessThanOrderByStdAsc(tenantId, start, end)
                .stream().map(DepartureSnapshotDto::of).toList();
    }

    private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
