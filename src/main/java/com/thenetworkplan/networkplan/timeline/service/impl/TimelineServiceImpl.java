package com.thenetworkplan.networkplan.timeline.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.OnTimePerformanceRule;
import com.thenetworkplan.networkplan.ops.service.OnTimePerformanceRule.Departure;
import com.thenetworkplan.networkplan.timeline.dto.TimelineDto;
import com.thenetworkplan.networkplan.timeline.dto.TimelineRowDto;
import com.thenetworkplan.networkplan.timeline.dto.TimelineSegmentDto;
import com.thenetworkplan.networkplan.timeline.service.FleetSection;
import com.thenetworkplan.networkplan.timeline.service.TimelineService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flight Timeline.
 *
 * <p>Four statements answer the whole screen: the fleet, the programme of the
 * window, the open MEL items per tail and the crew of each leg. The last two
 * come from the very services the dispatch board reads
 * ({@code findOpenMelByAircraft}, {@code findByLegIds}), so a MEL chip on a
 * timeline bar and a MEL flag on a dispatch row can never mean two different
 * things.
 *
 * <p>The ground segments are built by walking each tail's legs in order. This
 * is the only place in the product where what happens between two legs is
 * expressed, so the turnaround minimum is read from {@code OpsProperties} and
 * not repeated anywhere else.
 *
 * <p>Likewise on-time performance: measured by {@code OnTimePerformanceRule},
 * the same bean the dispatch board uses. The audited prototype counted it here
 * over every leg of the day, including those that had not departed, so a
 * morning with nothing gone read a hundred percent.
 */
@Service
@Transactional(readOnly = true)
public class TimelineServiceImpl implements TimelineService {

    private final LegRepository legRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftService aircraftService;
    private final CrewAssignmentService crewAssignmentService;
    private final OnTimePerformanceRule onTimePerformanceRule;
    private final OpsProperties opsProperties;

    public TimelineServiceImpl(LegRepository legRepository,
                               AircraftRepository aircraftRepository,
                               AircraftService aircraftService,
                               CrewAssignmentService crewAssignmentService,
                               OnTimePerformanceRule onTimePerformanceRule,
                               OpsProperties opsProperties) {
        this.legRepository = legRepository;
        this.aircraftRepository = aircraftRepository;
        this.aircraftService = aircraftService;
        this.crewAssignmentService = crewAssignmentService;
        this.onTimePerformanceRule = onTimePerformanceRule;
        this.opsProperties = opsProperties;
    }

    @Override
    public TimelineDto findTimeline(UUID tenantId, LocalDate from, int days, boolean includeIdle,
                                    String fleetSection, String baseIcao, String statusTone) {
        int window = Math.max(1, Math.min(days, 31));
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = start.plusDays(window);
        long minimumTurnaround = opsProperties.getMinimumTurnaround().toMinutes();

        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        List<Leg> programme = legRepository.findProgramme(tenantId, start, end);
        Map<UUID, List<Leg>> legsByAircraft = programme.stream()
                .collect(Collectors.groupingBy(
                        leg -> leg.getAircraft().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // La veille, pour que la fenetre ait quelque chose a quoi se comparer.
        // Meme requete, meme regle de retard : le seul ecart est la date.
        List<Leg> yesterday = legRepository.findProgramme(
                tenantId, start.minusDays(window), start);
        int flightsYesterday = yesterday.size();
        int delaysYesterday = (int) yesterday.stream().filter(leg -> delayMinutes(leg) > 0).count();

        Map<UUID, List<MelItemDto>> melByAircraft = aircraftService.findOpenMelByAircraft(tenantId);
        Map<UUID, LegCrewDto> crewByLeg = crewAssignmentService.findByLegIds(
                tenantId, programme.stream().map(Leg::getId).toList(), from);

        // The selector options are built from the whole fleet, before any
        // filter is applied: narrowing to one base must not make the other
        // bases disappear from the list that lets you go back.
        List<String> sections = fleet.stream()
                .map(aircraft -> FleetSection.of(icaoTypeOf(aircraft)))
                .distinct().sorted().toList();
        List<String> bases = new ArrayList<>(new TreeSet<>(fleet.stream()
                .map(this::baseOf)
                .filter(base -> base != null && !base.isBlank())
                .toList()));

        List<TimelineRowDto> rows = new ArrayList<>(fleet.size());
        List<Departure> departures = new ArrayList<>();
        int flights = 0;
        int tight = 0;
        int used = 0;
        int delays = 0;
        long totalBlockMinutes = 0;

        for (Aircraft aircraft : fleet) {
            String section = FleetSection.of(icaoTypeOf(aircraft));
            if (fleetSection != null && !fleetSection.equals(section)) {
                continue;
            }
            if (baseIcao != null && !baseIcao.equalsIgnoreCase(baseOf(aircraft))) {
                continue;
            }

            List<Leg> legs = legsByAircraft.getOrDefault(aircraft.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(Leg::getStd))
                    .toList();

            List<TimelineSegmentDto> segments = new ArrayList<>();
            long blockMinutes = 0;
            long groundMinutes = 0;
            int rowTight = 0;
            Leg previous = null;

            for (Leg leg : legs) {
                if (previous != null) {
                    OffsetDateTime groundStart = arrivalOf(previous);
                    OffsetDateTime groundEnd = departureOf(leg);
                    long minutes = Duration.between(groundStart, groundEnd).toMinutes();

                    // A turnaround is TIGHT when it is short but real. A negative
                    // ground period is not a short turnaround at all: it means the
                    // next leg departs before this one lands, which is an overlap.
                    // Folding the two together made every overlap wear the "below
                    // the minimum turnaround" label, and the lane drew a hatched
                    // bar reading "-210 min on the ground" — a sentence with no
                    // meaning. They are now two findings, and only one is tight.
                    boolean overlap = minutes < 0;
                    boolean tooTight = !overlap && minutes < minimumTurnaround;
                    if (tooTight) {
                        rowTight++;
                    }
                    groundMinutes += Math.max(0, minutes);

                    if (overlap) {
                        segments.add(ground(previous.getArrIcao(), leg.getDepIcao(),
                                groundEnd, groundStart, -minutes, false,
                                previous.getFlightNo() + " is still airborne when "
                                        + leg.getFlightNo() + " is planned to depart",
                                "OVERLAP"));
                    } else {
                        segments.add(ground(previous.getArrIcao(), leg.getDepIcao(),
                                groundStart, groundEnd, minutes, tooTight,
                                tooTight
                                        ? minutes + " min on the ground, below the " + minimumTurnaround
                                          + " min minimum turnaround"
                                        : null,
                                null));
                    }

                    if (!previous.getArrIcao().equals(leg.getDepIcao())) {
                        // The tail cannot be in two places: the rotation is broken.
                        // It is NOT a tight turnaround, and it used to be flagged as
                        // one only so the lane would draw it — which is how a
                        // 49-hour gap ended up hatched like a 45-minute turnaround.
                        // The flag now says what it means; the Optimize panel is
                        // where a broken rotation is reported.
                        segments.add(ground(previous.getArrIcao(), leg.getDepIcao(),
                                groundStart, groundEnd, minutes, false,
                                "Rotation broken: arrives " + previous.getArrIcao()
                                        + " and departs " + leg.getDepIcao(),
                                "BROKEN"));
                    }
                }

                long minutes = Duration.between(departureOf(leg), arrivalOf(leg)).toMinutes();
                int delayMinutes = delayMinutes(leg);
                if (delayMinutes > 0) {
                    delays++;
                }
                blockMinutes += minutes;
                flights++;
                departures.add(new Departure(leg.getStd(), leg.getOutAt()));

                MelItemDto mel = worstMel(melByAircraft.get(aircraft.getId()));
                LegCrewDto crew = crewByLeg.get(leg.getId());

                segments.add(new TimelineSegmentDto(
                        TimelineSegmentDto.FLIGHT,
                        leg.getId(),
                        leg.getFlightNo(),
                        leg.getDepIcao(),
                        leg.getArrIcao(),
                        departureOf(leg),
                        arrivalOf(leg),
                        minutes,
                        leg.getStatus().name(),
                        tone(leg, aircraft, delayMinutes),
                        delayMinutes,
                        mel == null ? null : mel.reference(),
                        mel != null && mel.blocksDispatch(),
                        crew == null ? "UNKNOWN" : crew.ftlStatus(),
                        false,
                        leg.getRemark()));
                previous = leg;
            }

            if (aircraft.getStatus() != AircraftStatus.SERVICEABLE) {
                // A grounded tail is a segment across the whole window: an OCC has to
                // see why the row is empty.
                MelItemDto mel = worstMel(melByAircraft.get(aircraft.getId()));
                segments.add(0, new TimelineSegmentDto(
                        TimelineSegmentDto.MAINTENANCE, null, null,
                        baseOf(aircraft), baseOf(aircraft),
                        start, end, Duration.between(start, end).toMinutes(),
                        aircraft.getStatus().name(),
                        aircraft.getStatus().name(),
                        0,
                        mel == null ? null : mel.reference(),
                        mel != null && mel.blocksDispatch(),
                        null,
                        true,
                        aircraft.getStatusReason()));
            }

            if (statusTone != null && segments.stream().noneMatch(
                    segment -> statusTone.equalsIgnoreCase(segment.statusTone()))) {
                continue;
            }
            if (legs.isEmpty() && !includeIdle && aircraft.getStatus() == AircraftStatus.SERVICEABLE) {
                continue;
            }
            if (!legs.isEmpty()) {
                used++;
            }

            tight += rowTight;
            totalBlockMinutes += blockMinutes;
            rows.add(new TimelineRowDto(
                    aircraft.getId(),
                    aircraft.getRegistration(),
                    icaoTypeOf(aircraft),
                    aircraft.getAircraftType() == null ? null : aircraft.getAircraftType().getModel(),
                    section,
                    baseOf(aircraft),
                    aircraft.getStatus().name(),
                    aircraft.getStatusReason(),
                    segments,
                    legs.size(),
                    blockMinutes,
                    groundMinutes,
                    rowTight));
        }

        // The header counts the whole fleet, not the filtered rows: an OCC
        // filtering on one base still needs to know how many aircraft the
        // operator has on the ground.
        int fleetSize = fleet.size();
        List<String> grounded = fleet.stream()
                .filter(aircraft -> aircraft.getStatus() != AircraftStatus.SERVICEABLE)
                .map(Aircraft::getRegistration)
                .sorted()
                .toList();
        int inService = fleetSize - grounded.size();
        OnTimePerformanceRule.Measure otp = onTimePerformanceRule.measure(departures);

        long referenceMinutes =
                (long) fleetSize * opsProperties.getDailyBlockHourReference() * 60 * window;

        return new TimelineDto(
                start, end, window, rows,
                used,
                rows.size() - used,
                flights,
                tight,
                (int) minimumTurnaround,
                fleetSize,
                inService,
                grounded.size(),
                grounded,
                fleetSize == 0 ? 0 : Math.round((inService * 100f) / fleetSize),
                delays,
                flightsYesterday,
                delaysYesterday,
                otp.percent(),
                otp.sample(),
                opsProperties.getOtpTargetPercent(),
                totalBlockMinutes,
                referenceMinutes == 0 ? 0 : Math.round((totalBlockMinutes * 100f) / referenceMinutes),
                opsProperties.getDailyBlockHourReference(),
                sections,
                bases,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    // ----------------------------------------------------------------

    private TimelineSegmentDto ground(String dep, String arr, OffsetDateTime from, OffsetDateTime to,
                                      long minutes, boolean tight, String note, String status) {
        return new TimelineSegmentDto(
                TimelineSegmentDto.GROUND, null, null, dep, arr, from, to, minutes,
                status, "GROUND", 0, null, false, null, tight, note);
    }

    /**
     * The state the bar is coloured by.
     *
     * <p>Order matters and is the dispatch board's: a cancelled leg is
     * cancelled whatever else is true, a grounded tail explains an empty lane
     * before anything else, and a late leg reads as late rather than as
     * scheduled.
     */
    private String tone(Leg leg, Aircraft aircraft, int delayMinutes) {
        if ("CANCELLED".equals(leg.getStatus().name())) {
            return "CANCELLED";
        }
        if (aircraft.getStatus() != AircraftStatus.SERVICEABLE) {
            return aircraft.getStatus() == AircraftStatus.AOG ? "AOG" : "MAINTENANCE";
        }
        if (delayMinutes > 0) {
            return "DELAYED";
        }
        return switch (leg.getStatus().name()) {
            case "DEPARTED" -> "ENROUTE";
            case "ARRIVED", "CLOSED" -> "CLOSED";
            default -> "SCHEDULED";
        };
    }

    /** Late by the operator's tolerance, measured on the revised departure. */
    private int delayMinutes(Leg leg) {
        if (leg.getEtd() == null || leg.getStd() == null) {
            return 0;
        }
        long minutes = Duration.between(leg.getStd(), leg.getEtd()).toMinutes();
        return minutes >= opsProperties.getDelayThreshold().toMinutes() ? (int) minutes : 0;
    }

    /** A blocking item outranks a deferred one; otherwise the first known. */
    private MelItemDto worstMel(List<MelItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .max(Comparator.comparing(MelItemDto::blocksDispatch))
                .orElse(null);
    }

    private String icaoTypeOf(Aircraft aircraft) {
        return aircraft.getAircraftType() == null ? null : aircraft.getAircraftType().getIcaoType();
    }

    private String baseOf(Aircraft aircraft) {
        return aircraft.getCurrentBaseIcao() != null
                ? aircraft.getCurrentBaseIcao() : aircraft.getHomeBaseIcao();
    }

    /** Actual time when it exists, estimate next, schedule last — never invented. */
    private OffsetDateTime departureOf(Leg leg) {
        if (leg.getOutAt() != null) {
            return leg.getOutAt();
        }
        return leg.getEtd() != null ? leg.getEtd() : leg.getStd();
    }

    private OffsetDateTime arrivalOf(Leg leg) {
        if (leg.getInAt() != null) {
            return leg.getInAt();
        }
        return leg.getEta() != null ? leg.getEta() : leg.getSta();
    }
}
