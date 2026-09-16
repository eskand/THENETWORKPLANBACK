package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.AirworthinessSnapshotDto;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.dto.ReadinessDto;
import com.thenetworkplan.networkplan.ops.dto.ReadinessItem;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.LegReadinessService;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.tripsupport.dto.CountryStatusDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The readiness engine.
 *
 * <p>Every finding below is computed from a persisted fact: an airworthiness
 * status, an open MEL row, an FTL verdict recorded at assignment, a permit
 * request status, a runway length from the AIP. Nothing is inferred from a
 * random number, and a missing input produces a finding rather than a pass —
 * which is the whole difference from the prototype's {@code dispatchReadiness()}.
 */
@Service
@Transactional(readOnly = true)
public class LegReadinessServiceImpl implements LegReadinessService {

    private final LegRepository legRepository;
    private final AircraftService aircraftService;
    private final CrewAssignmentService crewAssignmentService;
    private final PermitService permitService;
    private final GroundServiceService groundServiceService;
    private final AirportService airportService;

    public LegReadinessServiceImpl(LegRepository legRepository,
                                   AircraftService aircraftService,
                                   CrewAssignmentService crewAssignmentService,
                                   PermitService permitService,
                                   GroundServiceService groundServiceService,
                                   AirportService airportService) {
        this.legRepository = legRepository;
        this.aircraftService = aircraftService;
        this.crewAssignmentService = crewAssignmentService;
        this.permitService = permitService;
        this.groundServiceService = groundServiceService;
        this.airportService = airportService;
    }

    @Override
    public ReadinessDto assess(UUID tenantId, UUID legId) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));

        List<ReadinessItem> blocking = new ArrayList<>();
        List<ReadinessItem> derogable = new ArrayList<>();
        List<ReadinessItem> info = new ArrayList<>();

        checkAirworthiness(tenantId, leg, blocking, info);
        checkCrew(tenantId, leg, blocking, derogable, info);
        checkPermits(tenantId, leg, blocking, derogable);
        checkServices(tenantId, leg, derogable);
        checkRunways(leg, blocking, info);
        checkSlot(leg, info);

        return new ReadinessDto(legId, blocking.isEmpty(),
                List.copyOf(blocking), List.copyOf(derogable), List.copyOf(info));
    }

    private void checkAirworthiness(UUID tenantId, Leg leg,
                                    List<ReadinessItem> blocking, List<ReadinessItem> info) {
        AirworthinessSnapshotDto snapshot =
                aircraftService.findAirworthiness(tenantId, leg.getAircraft().getId());

        if (!"SERVICEABLE".equals(snapshot.aircraft().status())) {
            blocking.add(ReadinessItem.blocking("AIRCRAFT_STATUS",
                    snapshot.aircraft().registration() + " is " + snapshot.aircraft().status()
                            + (snapshot.aircraft().statusReason() == null
                            ? "" : ": " + snapshot.aircraft().statusReason()),
                    "Part-M / no release to service"));
        }
        if (snapshot.blockedByMel()) {
            String references = snapshot.openMelItems().stream()
                    .filter(MelItemDto::blocksDispatch)
                    .map(MelItemDto::reference)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            blocking.add(ReadinessItem.blocking("MEL_BLOCKING",
                    "MEL item forbids dispatch: " + references, "MMEL / operator MEL"));
        }
        for (MelItemDto item : snapshot.openMelItems()) {
            if (!item.blocksDispatch() && item.limitation() != null) {
                info.add(ReadinessItem.info("MEL_LIMITATION",
                        item.reference() + " (" + item.melCategory() + "): " + item.limitation(),
                        "MMEL / operator MEL"));
            }
        }
    }

    private void checkCrew(UUID tenantId, Leg leg, List<ReadinessItem> blocking,
                           List<ReadinessItem> derogable, List<ReadinessItem> info) {
        LegCrewDto crew = crewAssignmentService.findByLeg(tenantId, leg.getId(),
                leg.getStd().atZoneSameInstant(ZoneOffset.UTC).toLocalDate());

        if (!crew.complete()) {
            blocking.add(ReadinessItem.blocking("CREW_INCOMPLETE",
                    "Flight deck incomplete: " + crew.seatsFilled() + " of " + crew.minimumSeats()
                            + " seats assigned",
                    "ORO.FTL / minimum crew"));
        }
        switch (crew.ftlStatus()) {
            case "BREACH" -> blocking.add(ReadinessItem.blocking("CREW_FTL",
                    "An assignment is outside the flight-time limitations", "ORO.FTL.210"));
            case "WARNING" -> derogable.add(ReadinessItem.derogable("CREW_FTL",
                    "An assignment relies on a reduced rest or has no margin", "ORO.FTL.205 / 235"));
            case "UNKNOWN" -> derogable.add(ReadinessItem.derogable("CREW_FTL",
                    "Flight-time limitations could not be evaluated for this crew", "ORO.FTL"));
            default -> {
                // OK: nothing to report
            }
        }
        switch (crew.documentStatus()) {
            case "EXPIRED" -> blocking.add(ReadinessItem.blocking("CREW_DOCUMENTS",
                    "A licence, medical or recurrent training is expired on the day of the flight",
                    "Part-FCL / Part-MED"));
            case "EXPIRING" -> info.add(ReadinessItem.info("CREW_DOCUMENTS",
                    "A crew document expires within thirty days", "Part-FCL / Part-MED"));
            case "UNKNOWN" -> derogable.add(ReadinessItem.derogable("CREW_DOCUMENTS",
                    "A crew document expiry date is missing from the file", "Part-FCL / Part-MED"));
            default -> {
                // VALID: nothing to report
            }
        }
    }

    private void checkPermits(UUID tenantId, Leg leg,
                              List<ReadinessItem> blocking, List<ReadinessItem> derogable) {
        LegPermitsDto permits = permitService.findByLeg(tenantId, leg.getId());

        if (permits.outstandingCount() > 0) {
            blocking.add(ReadinessItem.blocking("PERMITS_OUTSTANDING",
                    permits.outstandingCount() + " permit request(s) not confirmed",
                    "State overflight / landing clearance"));
        }
        for (CountryStatusDto country : permits.countries()) {
            if ("NO_INSTRUMENT_KNOWN".equals(country.status())) {
                derogable.add(ReadinessItem.derogable("PERMIT_UNKNOWN",
                        "No known instrument for " + country.countryIso2()
                                + ": traffic rights must be established by hand",
                        "ASA corpus " + country.asaCorpusVersion()));
            }
        }
    }

    private void checkServices(UUID tenantId, Leg leg, List<ReadinessItem> derogable) {
        LegServicesDto services = groundServiceService.findByLeg(tenantId, leg.getId());
        if (!"READY".equals(services.readiness())) {
            derogable.add(ReadinessItem.derogable("SERVICES_PENDING",
                    services.confirmed() + " of " + services.total()
                            + " ground services confirmed",
                    "Operator ground handling procedure"));
        }
    }

    /**
     * A real aptitude check: the type's minimum runway against the longest runway
     * published for each station. The prototype had the data and never compared
     * the two on the strip.
     */
    private void checkRunways(Leg leg, List<ReadinessItem> blocking, List<ReadinessItem> info) {
        Integer required = leg.getAircraft().getAircraftType().getMinRunwayFt();
        if (required == null) {
            info.add(ReadinessItem.info("RUNWAY_DATA",
                    "No minimum runway length on file for "
                            + leg.getAircraft().getAircraftType().getIcaoType(),
                    "AFM"));
            return;
        }
        Map<String, AirportDto> stations =
                airportService.findAllByIcao(Set.of(leg.getDepIcao(), leg.getArrIcao()));

        for (String icao : List.of(leg.getDepIcao(), leg.getArrIcao())) {
            AirportDto airport = stations.get(icao);
            if (airport == null || airport.longestRunwayFt() == null) {
                info.add(ReadinessItem.info("RUNWAY_DATA",
                        "No published runway length for " + icao, "AIP"));
                continue;
            }
            if (airport.longestRunwayFt() < required) {
                blocking.add(ReadinessItem.blocking("RUNWAY_TOO_SHORT",
                        icao + " longest runway " + airport.longestRunwayFt()
                                + " ft is below the " + required + " ft required by "
                                + leg.getAircraft().getAircraftType().getIcaoType(),
                        "AFM take-off / landing distance"));
            }
        }
    }

    private void checkSlot(Leg leg, List<ReadinessItem> info) {
        if (leg.getCtot() != null) {
            info.add(ReadinessItem.info("SLOT_CTOT",
                    "ATFM slot in force, CTOT " + leg.getCtot(), "Network Manager"));
        }
    }
}
