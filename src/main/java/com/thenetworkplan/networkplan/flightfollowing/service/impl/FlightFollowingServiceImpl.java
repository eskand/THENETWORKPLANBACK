package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.flightfollowing.domain.PositionProvider;
import com.thenetworkplan.networkplan.flightfollowing.domain.PositionReport;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.flightfollowing.dto.FollowedFlightDto;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import com.thenetworkplan.networkplan.flightfollowing.service.LegRiskAssessor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import com.thenetworkplan.networkplan.flightfollowing.dto.FollowingBoardDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.PositionDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.ReportPositionCommand;
import com.thenetworkplan.networkplan.flightfollowing.mapper.FlightFollowingMapper;
import com.thenetworkplan.networkplan.flightfollowing.repository.PositionReportRepository;
import com.thenetworkplan.networkplan.flightfollowing.service.FlightFollowingService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flight Following.
 *
 * <p>The screen answers one question honestly: do we know where this aircraft
 * is. Three states — {@code LIVE}, {@code STALE}, {@code NO_SOURCE} — and the
 * provider of the last point, so a manually entered position is never
 * presented as a receiver's.
 *
 * <p>Nothing is interpolated. The audit found a simulator that produced
 * positions and random anomalies on its own; here, no row means no dot.
 */
@Service
@Transactional(readOnly = true)
public class FlightFollowingServiceImpl implements FlightFollowingService {

    /** Beyond this, the last position is old enough to be called stale. */
    private static final int STALE_MINUTES = 15;

    private final LegRepository legRepository;
    private final PositionReportRepository positionRepository;
    private final AircraftRepository aircraftRepository;
    private final FlightFollowingMapper mapper;
    private final AircraftService aircraftService;
    private final CrewAssignmentService crewAssignmentService;
    private final LegRiskAssessor legRiskAssessor;
    private final AdsbIngestService adsbIngestService;
    private final EntityManager entityManager;

    public FlightFollowingServiceImpl(LegRepository legRepository,
                                      PositionReportRepository positionRepository,
                                      AircraftRepository aircraftRepository,
                                      FlightFollowingMapper mapper,
                                      AircraftService aircraftService,
                                      CrewAssignmentService crewAssignmentService,
                                      LegRiskAssessor legRiskAssessor,
                                      AdsbIngestService adsbIngestService,
                                      EntityManager entityManager) {
        this.legRepository = legRepository;
        this.positionRepository = positionRepository;
        this.aircraftRepository = aircraftRepository;
        this.mapper = mapper;
        this.aircraftService = aircraftService;
        this.crewAssignmentService = crewAssignmentService;
        this.legRiskAssessor = legRiskAssessor;
        this.adsbIngestService = adsbIngestService;
        this.entityManager = entityManager;
    }

    @Override
    public FollowingBoardDto findBoard(UUID tenantId, LocalDate date) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        String operatorName = operatorName(tenantId);
        List<Leg> legs = legRepository.findProgramme(tenantId, start, start.plusDays(1));

        // Lire le flux avant de composer le tableau : la fenetre de
        // rafraichissement de AdsbProperties evite qu un ecran ouvert en
        // permanence n epuise le quota. Un echec ne fait rien echouer — les
        // dernieres positions connues restent, avec leur age.
        AdsbIngestService.Result adsb = adsbIngestService.ingest(tenantId);

        Map<UUID, PositionDto> lastByLeg = new HashMap<>();
        if (!legs.isEmpty()) {
            // Rows arrive newest first per leg, so the first one seen wins.
            for (PositionReport position : positionRepository.findForLegs(
                    tenantId, legs.stream().map(Leg::getId).toList())) {
                lastByLeg.putIfAbsent(position.getLegId(), mapper.toDto(position, now));
            }
        }

        // Memes sources que le tableau de dispatch et la timeline : un vol
        // amber ici et un vol amber la-bas parlent du meme fait.
        Map<UUID, List<MelItemDto>> melByAircraft = aircraftService.findOpenMelByAircraft(tenantId);
        Map<UUID, LegCrewDto> crewByLeg = crewAssignmentService.findByLegIds(
                tenantId, legs.stream().map(Leg::getId).toList(), date);

        List<FollowedFlightDto> airborne = new ArrayList<>();
        List<FollowedFlightDto> upcoming = new ArrayList<>();
        List<FollowedFlightDto> arrived = new ArrayList<>();
        int live = 0;
        int stale = 0;
        int noSource = 0;
        int riskLow = 0;
        int riskMedium = 0;
        int riskHigh = 0;
        int riskCritical = 0;

        for (Leg leg : legs) {
            PositionDto last = lastByLeg.get(leg.getId());
            String tracking;
            if (last == null) {
                tracking = "NO_SOURCE";
                noSource++;
            } else if (last.ageMinutes() > STALE_MINUTES) {
                tracking = "STALE";
                stale++;
            } else {
                tracking = "LIVE";
                live++;
            }

            OffsetDateTime arrival = leg.getEta() != null ? leg.getEta() : leg.getSta();
            Long minutesToDestination = leg.getStatus() == LegStatus.DEPARTED
                    ? Duration.between(now, arrival).toMinutes()
                    : null;

            MelItemDto mel = worstMel(melByAircraft.get(leg.getAircraft().getId()));
            LegCrewDto crew = crewByLeg.get(leg.getId());
            /* Le meme moteur que le tableau de dispatch lit — voir
               LegRiskAssessor : deux ecrans, une image du risque. */
            SmsRiskRule.Assessment risk = legRiskAssessor.assess(mel, crew);
            switch (risk.level()) {
                case "CRITICAL" -> riskCritical++;
                case "HIGH" -> riskHigh++;
                case "MEDIUM" -> riskMedium++;
                default -> riskLow++;
            }

            FollowedFlightDto dto = new FollowedFlightDto(
                    leg.getId(),
                    leg.getFlightNo(),
                    leg.getAircraft().getRegistration(),
                    leg.getAircraft().getAircraftType().getIcaoType(),
                    leg.getAircraft().getAircraftType().getModel(),
                    operatorName,
                    leg.getDepIcao(),
                    leg.getArrIcao(),
                    leg.getStd(),
                    leg.getSta(),
                    leg.getEta(),
                    leg.getStatus().name(),
                    tracking,
                    last,
                    minutesToDestination,
                    progressPercent(leg, now),
                    flightLevelOf(last),
                    mel == null ? null : mel.reference(),
                    mel != null && mel.blocksDispatch(),
                    risk);

            switch (leg.getStatus()) {
                case DEPARTED -> airborne.add(dto);
                case ARRIVED, CLOSED -> arrived.add(dto);
                default -> upcoming.add(dto);
            }
        }

        return new FollowingBoardDto(date, airborne, upcoming, arrived,
                live, stale, noSource, STALE_MINUTES,
                riskLow, riskMedium, riskHigh, riskCritical, adsb, now);
    }

    @Override
    public List<PositionDto> findTrack(UUID tenantId, UUID legId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return positionRepository.findTrack(tenantId, legId).stream()
                .map(position -> mapper.toDto(position, now))
                .toList();
    }

    @Override
    @Transactional
    public PositionDto report(UUID tenantId, ReportPositionCommand command, UUID actorId) {
        var aircraft = aircraftRepository.findOneWithType(tenantId, command.aircraftId())
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", command.aircraftId()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (command.reportedAt().isAfter(now.plusMinutes(2))) {
            throw new BusinessRuleException("POSITION_IN_FUTURE",
                    "A position cannot be reported in the future");
        }

        PositionProvider provider = parseProvider(command.provider());
        if (provider.isAutomatic() && (command.providerRef() == null || command.providerRef().isBlank())) {
            // An automatic provider has to say which feed and which message this
            // came from, or it is a manual entry wearing a receiver's name.
            throw new BusinessRuleException("POSITION_PROVIDER_REF_REQUIRED",
                    "A position attributed to " + provider + " must carry the feed reference it came from");
        }

        PositionReport position = new PositionReport();
        position.setTenantId(tenantId);
        position.setAircraft(aircraft);
        position.setLegId(command.legId());
        position.setReportedAt(command.reportedAt());
        position.setReceivedAt(now);
        position.setLatitude(command.latitude());
        position.setLongitude(command.longitude());
        position.setAltitudeFt(command.altitudeFt());
        position.setGroundSpeedKt(command.groundSpeedKt());
        position.setTrackDeg(command.trackDeg());
        position.setVerticalRateFpm(command.verticalRateFpm());
        position.setOnGround(command.onGround());
        position.setProvider(provider);
        position.setProviderRef(command.providerRef());
        position.getSource().setAuthor(actorId);

        return mapper.toDto(positionRepository.save(position), now);
    }

    /** FL de la derniere position connue, ou null si personne ne l a dit. */
    private Integer flightLevelOf(PositionDto position) {
        if (position == null || position.altitudeFt() == null) {
            return null;
        }
        return Math.round(position.altitudeFt() / 100f);
    }

    /**
     * La raison sociale de l exploitant, lue dans platform.tenants.
     *
     * <p>Une requete native plutot qu une entite : le nom est la seule colonne
     * dont ce module ait besoin, et lui donner une entite JPA ferait croire a
     * un agregat que personne n ecrit. Si la ligne manque, la fiche dit
     * "Operator unknown" au lieu d une chaine ecrite dans le code.
     */
    private String operatorName(UUID tenantId) {
        try {
            Object name = entityManager
                    .createNativeQuery("select name from platform.tenants where id = :id")
                    .setParameter("id", tenantId)
                    .getSingleResult();
            return name == null ? "Operator unknown" : name.toString();
        } catch (NoResultException notFound) {
            return "Operator unknown";
        }
    }

    /** Un item bloquant prime sur un item differe. */
    private MelItemDto worstMel(List<MelItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream().max(Comparator.comparing(MelItemDto::blocksDispatch)).orElse(null);
    }

    /**
     * Progress in time, not in distance: without a route there is no distance
     * flown, and guessing one is what the product must not do.
     */
    private Integer progressPercent(Leg leg, OffsetDateTime now) {
        if (leg.getStatus() != LegStatus.DEPARTED) {
            return null;
        }
        OffsetDateTime out = leg.getOutAt() != null ? leg.getOutAt() : leg.getStd();
        OffsetDateTime in = leg.getEta() != null ? leg.getEta() : leg.getSta();
        long total = Duration.between(out, in).toMinutes();
        if (total <= 0) {
            return null;
        }
        long elapsed = Duration.between(out, now).toMinutes();
        return (int) Math.max(0, Math.min(100, (elapsed * 100) / total));
    }

    private PositionProvider parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return PositionProvider.MANUAL;
        }
        try {
            return PositionProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("POSITION_PROVIDER_UNKNOWN", "Unknown provider: " + value);
        }
    }
}
