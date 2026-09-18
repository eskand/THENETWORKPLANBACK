package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegEvent;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.dto.OccEventDto;
import com.thenetworkplan.networkplan.ops.dto.OccTimelineDto;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import com.thenetworkplan.networkplan.ops.repository.LegEventRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.OccTimelineService;
import com.thenetworkplan.networkplan.ops.service.ReleaseService;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La frise OCC, calculee sur des faits.
 *
 * <p>La structure suit {@code computeOccTimeline()} de l'annexe (l. 13824) pas a
 * pas : memes dix evenements, meme ordre, memes fenetres relatives a l'ETD,
 * meme propagation de dependance a la fin. Ce qui change est la provenance de
 * chaque etat. Chez elle, {@code flight._occ.dispatchConfirmed} etait un booleen
 * pose par un clic et perdu au rechargement ; ici c'est la presence d'une
 * signature dans {@code ops.releases}. Chez elle, le carburant lisait
 * {@code flight._svcState.fuelDep} ; ici c'est le statut de la demande de
 * service FUEL a l'escale de depart.
 *
 * <p><b>Une difference assumee.</b> L'annexe traite un vol « enroute » comme
 * ayant forcement recu sa release et son creneau ({@code departedFallback},
 * l. 13857) — une deduction de confort pour que les donnees de demo n'affichent
 * pas de faux retards. Elle est reprise ici, parce que le raisonnement tient :
 * un appareil hors cale sans release est un ecart qui se traite ailleurs que
 * dans une frise, et le signaler ici ne ferait que noyer les vrais retards.
 */
@Service
@Transactional(readOnly = true)
public class OccTimelineServiceImpl implements OccTimelineService {

    /**
     * Combien de temps avant sa fenetre un evenement passe de « pending » a
     * « scheduled » — {@code OCC_PREP_WINDOW} de l'annexe (l. 9851).
     */
    private static final Duration PREP_WINDOW = Duration.ofHours(3);

    /**
     * Le delai de grace apres la fin d'une fenetre avant de la dire en retard —
     * {@code OCC_LATE_GRACE} (l. 9852).
     */
    private static final Duration LATE_GRACE = Duration.ofMinutes(15);

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /* Les six etats de l'annexe, OCC_STATUS_META (l. 13137). */
    private static final String PENDING = "PENDING";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final String DELAYED = "DELAYED";
    private static final String CANCELLED = "CANCELLED";

    private final LegRepository legRepository;
    private final LegEventRepository legEventRepository;
    private final ReleaseService releaseService;
    private final GroundServiceService groundServiceService;
    private final CrewAssignmentService crewAssignmentService;
    private final OpsProperties opsProperties;

    public OccTimelineServiceImpl(LegRepository legRepository,
                                  LegEventRepository legEventRepository,
                                  ReleaseService releaseService,
                                  GroundServiceService groundServiceService,
                                  CrewAssignmentService crewAssignmentService,
                                  OpsProperties opsProperties) {
        this.legRepository = legRepository;
        this.legEventRepository = legEventRepository;
        this.releaseService = releaseService;
        this.groundServiceService = groundServiceService;
        this.crewAssignmentService = crewAssignmentService;
        this.opsProperties = opsProperties;
    }

    @Override
    public OccTimelineDto timeline(UUID tenantId, UUID legId) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime std = leg.getStd();
        OffsetDateTime sta = leg.getSta();
        OffsetDateTime etd = leg.effectiveDeparture();
        OffsetDateTime eta = leg.effectiveArrival();
        boolean cancelled = leg.getStatus() == LegStatus.CANCELLED;

        List<LegEvent> history = legEventRepository
                .findByTenantIdAndLegIdOrderByCreatedAtDesc(tenantId, legId);
        LegServicesDto services = groundServiceService.findByLeg(tenantId, legId);
        Optional<ReleaseDto> release = releaseService.findCurrent(tenantId, legId);
        LegCrewDto crew = crewAssignmentService.findByLeg(tenantId, legId,
                std.atZoneSameInstant(ZoneOffset.UTC).toLocalDate());

        Context context = new Context(now, cancelled);
        List<OccEventDto> events = new ArrayList<>(10);

        /* 1) Flight Creation — l'etape existe. Chez l'annexe, une heure
           conventionnelle (STD moins 24 h) ; ici, l'heure ou la ligne a
           reellement ete ecrite, quand le journal la porte. */
        OffsetDateTime createdAt = history.stream()
                .filter(event -> event.getKind() == LegEventKind.CREATED)
                .map(LegEvent::getCreatedAt)
                .findFirst()
                .orElse(std.minusHours(24));
        events.add(new OccEventDto("creation", "Flight Creation", createdAt, createdAt,
                cancelled ? CANCELLED : COMPLETED, null, null));

        /* Un appareil hors cale a forcement ete lache : voir l'en-tete. */
        Link departed = leg.getStatus().isAirborneOrLater() ? Link.CONFIRMED : null;

        /* 2) Dispatch Release — la signature, et l'accuse du commandant. */
        Link releaseLink = release.map(signed -> Link.CONFIRMED).orElse(departed);
        String releaseNote = release
                .map(signed -> {
                    String line = "Released " + utc(signed.signedAt())
                            + (signed.acknowledged()
                            ? " · acknowledged by the commander " + utc(signed.captainAckAt())
                            : " · awaiting the commander's acknowledgement");
                    return signed.derogation()
                            ? line + " · derogation: " + signed.derogationReason()
                            : line;
                })
                .orElse(null);
        events.add(context.event("dispatch", "Dispatch Release",
                etd.minusMinutes(90), etd.minusMinutes(75), releaseLink, releaseNote,
                release.isEmpty() && !cancelled && !leg.getStatus().isAirborneOrLater()
                        ? "CONFIRM_RELEASE" : null));

        /* 3) Fuel Release — la demande FUEL a l'escale de depart. */
        Link fuelLink = link(services, leg.getDepIcao(), "FUEL");
        events.add(context.event("fuel", "Fuel Release",
                etd.minusMinutes(75), etd.minusMinutes(60), orElse(fuelLink, departed),
                note(services, leg.getDepIcao(), "FUEL"), null));

        /* 4) Crew Report — presentation de l'equipage, avec le verdict FTL
              enregistre a l'affectation. Une affectation illegale bloque
              reellement la release : elle se lit ici comme un retard, pas comme
              une mention. */
        Link crewLink = null;
        String crewNote = null;
        if (!crew.members().isEmpty() || crew.minimumSeats() > 0) {
            if (!crew.complete()) {
                crewLink = Link.PENDING;
                crewNote = "Crew incomplete — " + crew.seatsFilled() + " of "
                        + crew.minimumSeats() + " seats assigned; assign the rest in the Crew tab.";
            } else {
                switch (crew.ftlStatus()) {
                    case "BREACH" -> {
                        crewLink = Link.DENIED;
                        crewNote = "FTL breach (ORO.FTL / CS FTL.1) — reassign before release.";
                    }
                    case "WARNING" -> {
                        crewLink = Link.PENDING;
                        crewNote = "FTL warning — reduced rest or no margin; review before release.";
                    }
                    case "UNKNOWN" -> {
                        crewLink = Link.PENDING;
                        crewNote = "Flight-time limitations could not be evaluated for this crew.";
                    }
                    default -> crewLink = Link.CONFIRMED;
                }
                if (crewLink == Link.CONFIRMED && "EXPIRED".equals(crew.documentStatus())) {
                    crewLink = Link.DENIED;
                    crewNote = "A licence, medical or recurrent training is expired on the day "
                            + "of the flight (Part-FCL / Part-MED).";
                }
            }
        }
        events.add(context.event("crew", "Crew Report",
                etd.minusMinutes(60), etd.minusMinutes(45), crewLink, crewNote, null));

        /* 5) ATC Slot — le CTOT et sa reference. Le bouton reste offert une fois
              le creneau pose : un creneau se revise, et c'est la revision qui
              recalcule la suite. */
        Link slotLink = leg.getCtot() != null ? Link.CONFIRMED : departed;
        String slotNote = leg.getCtot() == null ? null
                : "CTOT " + utc(leg.getCtot())
                + (leg.getCtotRef() == null || leg.getCtotRef().isBlank()
                ? "" : " — ref " + leg.getCtotRef());
        events.add(context.event("slot", "ATC Slot",
                etd.minusMinutes(45), etd.minusMinutes(30), slotLink, slotNote,
                cancelled || leg.getStatus().isAirborneOrLater() ? null : "SET_SLOT"));

        /* 6) Ground Handling — la demande HANDLING a l'escale de depart. */
        Link groundLink = link(services, leg.getDepIcao(), "HANDLING");
        events.add(context.event("ground", "Ground Handling",
                etd.minusMinutes(75), etd.minusMinutes(15), orElse(groundLink, departed),
                note(services, leg.getDepIcao(), "HANDLING"), null));

        /* 7) Refueling — l'avitaillement physique ; il depend de la meme
              demande que la mise a disposition du carburant. */
        events.add(context.event("refuel", "Refueling",
                etd.minusMinutes(50), etd.minusMinutes(20), orElse(fuelLink, departed), null, null));

        /* 8) Turnaround — le temps au sol depuis l'arrivee precedente du meme
              appareil, compare au minimum de l'exploitant. */
        List<Leg> rotation = legRepository.findRotation(tenantId, leg.getAircraft().getId(),
                std.minusDays(1), std.plusDays(1));
        Leg previous = null;
        Leg next = null;
        for (Leg other : rotation) {
            if (other.getId().equals(legId)) {
                continue;
            }
            if (other.getStd().isBefore(std)) {
                previous = other;
            } else if (next == null) {
                next = other;
            }
        }
        Duration minimum = opsProperties.getMinimumTurnaround();
        if (previous != null) {
            OffsetDateTime previousArrival = previous.effectiveArrival();
            Duration gap = Duration.between(previousArrival, etd);
            boolean insufficient = gap.compareTo(minimum) < 0 && !cancelled;
            events.add(new OccEventDto("turnaround", "Turnaround", previousArrival, etd,
                    cancelled ? CANCELLED
                            : insufficient ? DELAYED
                            : context.classify(previousArrival, etd,
                            now.isBefore(etd) ? null : Link.CONFIRMED),
                    insufficient
                            ? "Only " + gap.toMinutes() + " min ground time (minimum "
                            + minimum.toMinutes() + " min) after "
                            + label(previous)
                            : gap.toMinutes() + " min ground time after " + label(previous),
                    null));
        } else {
            events.add(new OccEventDto("turnaround", "Turnaround", std, std,
                    cancelled ? CANCELLED : COMPLETED,
                    "First rotation of the day for " + leg.getAircraft().getRegistration() + ".",
                    null));
        }

        /* 9) Flight Closed — la cloture apres vol, conditionnee au MVT ARR. */
        boolean arrived = !now.isBefore(eta);
        Link closedLink = arrived && leg.getMvtSentAt() != null ? Link.CONFIRMED : null;
        events.add(context.event("closed", "Flight Closed",
                eta.plusMinutes(15), eta.plusMinutes(30), closedLink,
                arrived && leg.getMvtSentAt() == null
                        ? "Awaiting the MVT arrival message before close-out."
                        : leg.getMvtSentAt() != null ? "MVT sent " + utc(leg.getMvtSentAt()) : null,
                null));

        /* 10) Next Flight — l'impact sur la rotation suivante du meme appareil. */
        if (next != null) {
            OffsetDateTime nextDeparture = next.effectiveDeparture();
            Duration gap = Duration.between(eta, nextDeparture);
            boolean cascading = gap.compareTo(minimum) < 0 && !cancelled;
            events.add(new OccEventDto("nextflight", "Next Flight", eta, nextDeparture,
                    cancelled ? CANCELLED
                            : cascading ? DELAYED
                            : !now.isBefore(nextDeparture) ? COMPLETED
                            : !now.isBefore(eta) ? SCHEDULED : PENDING,
                    cascading
                            ? "Rotation risk — " + (minimum.toMinutes() - gap.toMinutes())
                            + " min short before " + label(next) + " (" + utc(nextDeparture) + ")"
                            : label(next) + " · " + utc(nextDeparture),
                    null));
        } else {
            events.add(new OccEventDto("nextflight", "Next Flight", eta, eta,
                    cancelled ? CANCELLED : COMPLETED,
                    "Last rotation of the day for " + leg.getAircraft().getRegistration() + ".",
                    null));
        }

        List<OccEventDto> propagated = propagate(events);
        long delayMinutes = Math.max(0, Duration.between(std, etd).toMinutes());

        return new OccTimelineDto(legId, leg.getFlightNo(),
                leg.getAircraft().getRegistration(), leg.getDepIcao(), leg.getArrIcao(),
                now, std, sta, etd, eta, delayMinutes,
                delayMinutes > 0 ? delayReason(history) : null,
                leg.getStatus().name(),
                propagated.stream().anyMatch(event -> DELAYED.equals(event.status())),
                propagated);
    }

    /**
     * La propagation de dependance de l'annexe (l. 13957).
     *
     * <p>Un evenement ne peut pas avoir l'air serein quand ce dont il depend est
     * en retard : sans cette passe, une frise affiche « Refueling — pending »
     * alors que la release qui l'autorise est bloquee.
     */
    private List<OccEventDto> propagate(List<OccEventDto> events) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        dependencies.put("fuel", List.of("dispatch"));
        dependencies.put("crew", List.of("dispatch"));
        dependencies.put("slot", List.of("dispatch"));
        dependencies.put("ground", List.of("dispatch"));
        dependencies.put("refuel", List.of("fuel"));
        dependencies.put("closed", List.of("ground", "fuel", "crew", "slot"));

        Map<String, OccEventDto> byKey = new LinkedHashMap<>();
        events.forEach(event -> byKey.put(event.key(), event));

        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            OccEventDto event = byKey.get(entry.getKey());
            if (event == null
                    || COMPLETED.equals(event.status())
                    || CANCELLED.equals(event.status())
                    || (!PENDING.equals(event.status()) && !SCHEDULED.equals(event.status()))) {
                continue;
            }
            OccEventDto blocking = entry.getValue().stream()
                    .map(byKey::get)
                    .filter(dependency -> dependency != null
                            && (DELAYED.equals(dependency.status())
                            || CANCELLED.equals(dependency.status())))
                    .findFirst()
                    .orElse(null);
            if (blocking != null) {
                byKey.put(entry.getKey(), new OccEventDto(event.key(), event.label(),
                        event.start(), event.end(), DELAYED,
                        event.note() != null ? event.note() : "Blocked pending " + blocking.label(),
                        event.action()));
            }
        }
        return List.copyOf(byKey.values());
    }

    /** La raison du dernier retard enregistre, quand le journal en porte une. */
    private String delayReason(List<LegEvent> history) {
        return history.stream()
                .filter(event -> event.getKind() == LegEventKind.DELAYED
                        || event.getKind() == LegEventKind.MOVED)
                .map(LegEvent::getReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * L'etat d'une demande de service, lu comme {@code occSvcLink()} de l'annexe
     * (l. 13660) : un refus l'emporte sur tout, puis l'absence de confirmation.
     */
    private Link link(LegServicesDto services, String station, String serviceType) {
        List<ServiceRequestDto> matching = services.requests().stream()
                .filter(request -> serviceType.equals(request.serviceType()))
                .filter(request -> station.equalsIgnoreCase(request.stationIcao()))
                .toList();
        if (matching.isEmpty()) {
            return null;
        }
        if (matching.stream().anyMatch(request -> "REFUSED".equals(request.status()))) {
            return Link.DENIED;
        }
        if (matching.stream().allMatch(request -> "CONFIRMED".equals(request.status()))) {
            return Link.CONFIRMED;
        }
        return Link.PENDING;
    }

    /** Le fournisseur et la reference, quand la demande les porte. */
    private String note(LegServicesDto services, String station, String serviceType) {
        return services.requests().stream()
                .filter(request -> serviceType.equals(request.serviceType()))
                .filter(request -> station.equalsIgnoreCase(request.stationIcao()))
                .findFirst()
                .map(request -> {
                    String supplier = request.supplierName() == null
                            ? "No supplier on the request" : request.supplierName();
                    return supplier + " · " + request.status()
                            + (request.reference() == null || request.reference().isBlank()
                            ? "" : " · ref " + request.reference());
                })
                .orElse("No " + serviceType.toLowerCase() + " request at " + station + " yet.");
    }

    private Link orElse(Link link, Link fallback) {
        return link != null ? link : fallback;
    }

    private String label(Leg leg) {
        return leg.getFlightNo() != null && !leg.getFlightNo().isBlank()
                ? leg.getFlightNo()
                : leg.getDepIcao() + "–" + leg.getArrIcao();
    }

    private String utc(OffsetDateTime when) {
        return when == null ? "—" : HHMM.format(when.atZoneSameInstant(ZoneOffset.UTC)) + "Z";
    }

    /** Ce qu'une source confirme, refuse ou laisse en attente. */
    private enum Link { CONFIRMED, PENDING, DENIED }

    /**
     * Le classement d'un evenement dans le temps — {@code classify()} de
     * l'annexe (l. 13832), au mot pres.
     */
    private record Context(OffsetDateTime now, boolean cancelled) {

        OccEventDto event(String key, String label, OffsetDateTime start, OffsetDateTime end,
                          Link link, String note, String action) {
            String status = classify(start, end, link);
            return new OccEventDto(key, label, start, end, status, note,
                    COMPLETED.equals(status) || CANCELLED.equals(status) ? null : action);
        }

        String classify(OffsetDateTime start, OffsetDateTime end, Link link) {
            if (cancelled) {
                return CANCELLED;
            }
            if (link == Link.DENIED) {
                return DELAYED;
            }
            if (link == Link.CONFIRMED) {
                if (!now.isBefore(end)) {
                    return COMPLETED;
                }
                return !now.isBefore(start) ? IN_PROGRESS : SCHEDULED;
            }
            if (!now.isBefore(end.plus(LATE_GRACE))) {
                return DELAYED;
            }
            if (!now.isBefore(start)) {
                return IN_PROGRESS;
            }
            return !now.isBefore(start.minus(PREP_WINDOW)) ? SCHEDULED : PENDING;
        }
    }
}
