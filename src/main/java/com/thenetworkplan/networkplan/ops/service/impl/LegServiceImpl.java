package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.repository.MelItemRepository;
import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import com.thenetworkplan.networkplan.ops.domain.FlightType;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.dto.CancelLegCommand;
import com.thenetworkplan.networkplan.ops.dto.ChangeAircraftCommand;
import com.thenetworkplan.networkplan.ops.dto.CreateLegCommand;
import com.thenetworkplan.networkplan.ops.dto.DelayCodeDto;
import com.thenetworkplan.networkplan.ops.dto.LegDelayDto;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.MoveLegCommand;
import com.thenetworkplan.networkplan.ops.dto.SetSlotCommand;
import com.thenetworkplan.networkplan.ops.dto.StationCount;
import com.thenetworkplan.networkplan.ops.mapper.LegMapper;
import com.thenetworkplan.networkplan.ops.repository.DelayCodeRepository;
import com.thenetworkplan.networkplan.ops.repository.DelayRecordRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import com.thenetworkplan.networkplan.ops.service.LegService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LegServiceImpl implements LegService {

    private static final Logger LOG = LoggerFactory.getLogger(LegServiceImpl.class);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LegRepository legRepository;
    private final AircraftRepository aircraftRepository;
    private final MelItemRepository melItemRepository;
    private final DelayRecordRepository delayRecordRepository;
    private final DelayCodeRepository delayCodeRepository;
    private final LegEventRecorder eventRecorder;
    private final LegMapper mapper;
    private final OpsProperties opsProperties;

    public LegServiceImpl(LegRepository legRepository,
                          AircraftRepository aircraftRepository,
                          MelItemRepository melItemRepository,
                          DelayRecordRepository delayRecordRepository,
                          DelayCodeRepository delayCodeRepository,
                          LegEventRecorder eventRecorder,
                          LegMapper mapper,
                          OpsProperties opsProperties) {
        this.legRepository = legRepository;
        this.aircraftRepository = aircraftRepository;
        this.melItemRepository = melItemRepository;
        this.delayRecordRepository = delayRecordRepository;
        this.delayCodeRepository = delayCodeRepository;
        this.eventRecorder = eventRecorder;
        this.mapper = mapper;
        this.opsProperties = opsProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelayCodeDto> delayCodes(UUID tenantId) {
        return delayCodeRepository.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId).stream()
                .map(code -> new DelayCodeDto(code.getCode(), code.getLabel()))
                .toList();
    }

    @Override
    public List<LegDto> findProgramme(UUID tenantId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        return legRepository.findProgramme(tenantId, from, from.plusDays(1)).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public LegDto findById(UUID tenantId, UUID legId) {
        return mapper.toDto(requireLeg(tenantId, legId));
    }

    @Override
    public List<LegDto> findProgrammeRange(UUID tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return legRepository.findProgramme(tenantId, start, end).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<LegDelayDto> findDelays(UUID tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return delayRecordRepository.findInWindow(tenantId, start, end).stream()
                .map(record -> new LegDelayDto(record.getLegId(), record.getMinutes(),
                        record.getCode(), record.getSubCode(), record.getRemark()))
                .toList();
    }

    /**
     * @return code to {@code [minutes, occurrences]} — two figures per code,
     *         which is what an IATA delay report is made of
     */
    @Override
    public Map<String, long[]> countDelaysByCode(UUID tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        Map<String, long[]> byCode = new LinkedHashMap<>();
        for (DelayRecord record : delayRecordRepository.findInWindow(tenantId, start, end)) {
            long[] figures = byCode.computeIfAbsent(record.getCode(), key -> new long[2]);
            figures[0] += record.getMinutes();
            figures[1] += 1;
        }
        return byCode;
    }

    /**
     * Two grouped queries, whatever the number of stations. DOM8 asks this
     * question instead of reading {@code ops.legs}, which keeps the aerodrome
     * directory out of the flight-operations schema.
     */
    @Override
    public Map<String, Map<String, Long>> countStationUsage(UUID tenantId, LocalDate since) {
        OffsetDateTime from = since.atStartOfDay().atOffset(ZoneOffset.UTC);
        Map<String, Long> departures = legRepository.countDeparturesByStation(tenantId, from).stream()
                .collect(Collectors.toMap(StationCount::icao, StationCount::value));
        Map<String, Long> arrivals = legRepository.countArrivalsByStation(tenantId, from).stream()
                .collect(Collectors.toMap(StationCount::icao, StationCount::value));
        return Map.of("DEP", departures, "ARR", arrivals);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto create(UUID tenantId, CreateLegCommand command, UUID actorId) {
        if (!command.sta().isAfter(command.std())) {
            throw new BusinessRuleException("LEG_TIMES_INVALID", "STA must be after STD");
        }
        Aircraft aircraft = requireAircraft(tenantId, command.registration());

        Leg leg = new Leg();
        leg.setTenantId(tenantId);
        leg.setAircraft(aircraft);
        leg.setFlightNo(command.flightNo().trim().toUpperCase());
        leg.setDepIcao(command.depIcao().trim().toUpperCase());
        leg.setArrIcao(command.arrIcao().trim().toUpperCase());
        leg.setBaseIcao(aircraft.getHomeBaseIcao());
        leg.setStd(command.std());
        leg.setSta(command.sta());
        leg.setEtd(command.std());
        leg.setEta(command.sta());
        leg.setPaxCount(command.paxCount());
        leg.setStatus(LegStatus.PLANNED);
        leg.setFlightType(parseFlightType(command.flightType()));
        leg.setBusinessKey(businessKey(aircraft.getRegistration(), command.std(), leg.getFlightNo()));
        leg.setSource(Source.manual(actorId, "dispatch: add flight"));

        if (legRepository.existsByBusinessKey(leg.getBusinessKey())) {
            throw new BusinessRuleException("LEG_ALREADY_EXISTS",
                    "A leg already exists with the key " + leg.getBusinessKey());
        }

        Leg saved = legRepository.save(leg);
        eventRecorder.record(tenantId, saved.getId(), LegEventKind.CREATED, null,
                snapshot(saved), actorId, "Leg created from dispatch");
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto move(UUID tenantId, UUID legId, MoveLegCommand command, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus().isAirborneOrLater()) {
            throw new BusinessRuleException("LEG_ALREADY_DEPARTED",
                    "A leg that is off blocks cannot be rescheduled");
        }
        if (!command.sta().isAfter(command.std())) {
            throw new BusinessRuleException("LEG_TIMES_INVALID", "STA must be after STD");
        }

        Map<String, Object> before = snapshot(leg);
        long delayMinutes = Duration.between(leg.effectiveDeparture(), command.std()).toMinutes();

        leg.setStd(command.std());
        leg.setSta(command.sta());
        leg.setEtd(command.std());
        leg.setEta(command.sta());
        Leg saved = legRepository.save(leg);

        eventRecorder.record(tenantId, legId, LegEventKind.MOVED, before, snapshot(saved),
                actorId, command.reason());

        if (delayMinutes > 0) {
            recordDelay(tenantId, legId, (int) delayMinutes, command.delayCode(), command.reason());
            cascade(tenantId, saved, actorId);
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto changeAircraft(UUID tenantId, UUID legId, ChangeAircraftCommand command, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus().isAirborneOrLater()) {
            throw new BusinessRuleException("LEG_ALREADY_DEPARTED",
                    "The tail of a leg that is off blocks cannot be changed");
        }
        Aircraft target = requireAircraft(tenantId, command.registration());

        // FR38: the airworthiness guard. Read from DOM5, never assumed.
        if (target.getStatus() != AircraftStatus.SERVICEABLE) {
            throw new BusinessRuleException("AIRCRAFT_NOT_RELEASABLE",
                    target.getRegistration() + " is " + target.getStatus()
                            + " and cannot be assigned to a leg");
        }
        boolean blockedByMel = melItemRepository.findOpenForAircraft(tenantId, target.getId()).stream()
                .anyMatch(MelItem::isBlocksDispatch);
        if (blockedByMel) {
            throw new BusinessRuleException("AIRCRAFT_MEL_BLOCKING",
                    target.getRegistration() + " carries a MEL item that forbids dispatch");
        }

        Map<String, Object> before = snapshot(leg);
        leg.setAircraft(target);
        leg.setBaseIcao(target.getHomeBaseIcao());
        Leg saved = legRepository.save(leg);

        eventRecorder.record(tenantId, legId, LegEventKind.AIRCRAFT_CHANGED, before,
                snapshot(saved), actorId, command.reason());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto cancel(UUID tenantId, UUID legId, CancelLegCommand command, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus() == LegStatus.CLOSED) {
            throw new BusinessRuleException("LEG_CLOSED", "A closed leg cannot be cancelled");
        }
        if (leg.getStatus() == LegStatus.CANCELLED) {
            return mapper.toDto(leg);
        }
        Map<String, Object> before = snapshot(leg);
        leg.setStatus(LegStatus.CANCELLED);
        Leg saved = legRepository.save(leg);
        eventRecorder.record(tenantId, legId, LegEventKind.CANCELLED, before, snapshot(saved),
                actorId, command.reason());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto setSlot(UUID tenantId, UUID legId, SetSlotCommand command, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus().isAirborneOrLater()) {
            throw new BusinessRuleException("LEG_ALREADY_DEPARTED",
                    "A leg that is off blocks no longer holds a departure slot");
        }
        if (leg.getStatus() == LegStatus.CANCELLED) {
            throw new BusinessRuleException("LEG_CANCELLED",
                    "A cancelled leg cannot be given a slot");
        }

        Map<String, Object> before = snapshot(leg);
        OffsetDateTime previousDeparture = leg.effectiveDeparture();
        String reference = command.reference() == null || command.reference().isBlank()
                ? null : command.reference().trim();

        leg.setCtot(command.ctot());
        leg.setCtotRef(reference);

        // Le creneau ne deplace l'estimation que lorsqu'il la repousse : un CTOT
        // plus tot que l'ETD courant ne fait pas partir plus tot, il laisse
        // simplement de la marge. L'horaire publie (STD/STA) ne bouge jamais —
        // c'est contre lui que la ponctualite se mesure.
        long push = Duration.between(previousDeparture, command.ctot()).toMinutes();
        if (push > 0) {
            leg.setEtd(command.ctot());
            leg.setEta(leg.effectiveArrival().plusMinutes(push));
        }
        Leg saved = legRepository.save(leg);

        String reason = "ATC slot — CTOT "
                + command.ctot().atZoneSameInstant(ZoneOffset.UTC).toLocalTime()
                        .withSecond(0).withNano(0)
                + "Z" + (reference == null ? "" : " (ref " + reference + ")");
        eventRecorder.record(tenantId, legId,
                push > 0 ? LegEventKind.DELAYED : LegEventKind.REMARK,
                before, snapshot(saved), actorId, reason);

        if (push > 0) {
            // 81 — ATFM due to ATC en-route demand/capacity. Un creneau qui
            // repousse le depart EST un retard reglementaire : le coder autrement
            // sortirait la cause du releve de ponctualite.
            recordDelay(tenantId, legId, (int) push, "81", reason);
            cascade(tenantId, saved, actorId);
        }
        return mapper.toDto(saved);
    }

    /**
     * FR38 cascade: a delay propagates to the next rotations of the same tail
     * only while the turnaround stays under the operator minimum.
     */
    private void cascade(UUID tenantId, Leg delayed, UUID actorId) {
        Duration minimum = opsProperties.getMinimumTurnaround();
        OffsetDateTime inbound = delayed.effectiveArrival();

        for (Leg next : legRepository.findNextRotations(tenantId, delayed.getAircraft().getId(),
                delayed.getStd())) {
            Duration turnaround = Duration.between(inbound, next.effectiveDeparture());
            if (turnaround.compareTo(minimum) >= 0) {
                break;
            }
            Map<String, Object> before = snapshot(next);
            Duration push = minimum.minus(turnaround);
            next.setEtd(next.effectiveDeparture().plus(push));
            next.setEta(next.effectiveArrival().plus(push));
            Leg saved = legRepository.save(next);
            eventRecorder.record(tenantId, saved.getId(), LegEventKind.DELAYED, before,
                    snapshot(saved), actorId,
                    "Cascade: turnaround below " + minimum.toMinutes() + " minutes after "
                            + delayed.getFlightNo());
            inbound = saved.effectiveArrival();
            LOG.debug("Cascaded {} minutes onto {}", push.toMinutes(), saved.getFlightNo());
        }
    }

    private void recordDelay(UUID tenantId, UUID legId, int minutes, String code, String remark) {
        String effectiveCode = code == null || code.isBlank() ? "93" : code.trim();
        delayCodeRepository.findByTenantIdAndCode(tenantId, effectiveCode)
                .orElseThrow(() -> new BusinessRuleException("DELAY_CODE_UNKNOWN",
                        "Delay code " + effectiveCode + " is not in the tenant list"));

        DelayRecord record = new DelayRecord();
        record.setTenantId(tenantId);
        record.setLegId(legId);
        record.setMinutes(minutes);
        record.setCode(effectiveCode);
        record.setRemark(remark);
        delayRecordRepository.save(record);
    }

    private Leg requireLeg(UUID tenantId, UUID legId) {
        return legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
    }

    private Aircraft requireAircraft(UUID tenantId, String registration) {
        return aircraftRepository.findByRegistration(tenantId, registration.trim().toUpperCase())
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", registration));
    }

    private FlightType parseFlightType(String raw) {
        if (raw == null || raw.isBlank()) {
            return FlightType.PAX;
        }
        try {
            return FlightType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("FLIGHT_TYPE_UNKNOWN", "Unknown flight type: " + raw);
        }
    }

    /** Stable key: registration, date of STD and flight number. Never regenerated. */
    private String businessKey(String registration, OffsetDateTime std, String flightNo) {
        return registration + "/" + KEY_DATE.format(std.atZoneSameInstant(ZoneOffset.UTC)) + "/" + flightNo;
    }

    private Map<String, Object> snapshot(Leg leg) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("flightNo", leg.getFlightNo());
        values.put("registration", leg.getAircraft().getRegistration());
        values.put("depIcao", leg.getDepIcao());
        values.put("arrIcao", leg.getArrIcao());
        values.put("std", leg.getStd());
        values.put("sta", leg.getSta());
        values.put("etd", leg.getEtd());
        values.put("eta", leg.getEta());
        values.put("status", leg.getStatus());
        return values;
    }
}
