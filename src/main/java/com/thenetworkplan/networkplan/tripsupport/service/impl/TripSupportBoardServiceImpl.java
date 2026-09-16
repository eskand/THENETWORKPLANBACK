package com.thenetworkplan.networkplan.tripsupport.service.impl;

import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.tripsupport.domain.GroundServiceType;
import com.thenetworkplan.networkplan.tripsupport.domain.PermitRequest;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.ServiceRequest;
import com.thenetworkplan.networkplan.tripsupport.domain.Supplier;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.PermitRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.SupplierDto;
import com.thenetworkplan.networkplan.tripsupport.dto.TripSupportBoardDto;
import com.thenetworkplan.networkplan.tripsupport.dto.TripSupportRowDto;
import com.thenetworkplan.networkplan.tripsupport.mapper.TripSupportMapper;
import com.thenetworkplan.networkplan.tripsupport.repository.PermitRequestRepository;
import com.thenetworkplan.networkplan.tripsupport.repository.ServiceRequestRepository;
import com.thenetworkplan.networkplan.tripsupport.repository.SupplierRepository;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.TripSupportBoardService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NetPlus Services.
 *
 * <p>Five statements answer the whole board, whatever the size of the day: the
 * legs, the service requests, the permit requests, the service readiness
 * aggregate, and the suppliers of the stations involved. Nothing is queried
 * inside the loop — the same discipline as the dispatch board.
 *
 * <p>The board answers three questions per leg: what has been requested, what
 * has been confirmed, and what has <em>not</em> been requested at all. The
 * third is the one the prototype could not answer.
 */
@Service
@Transactional(readOnly = true)
public class TripSupportBoardServiceImpl implements TripSupportBoardService {

    /**
     * The services an operator expects on a normal turnaround. A leg missing
     * one of these is flagged; the others are requested when the trip needs
     * them, and their absence is not a finding.
     */
    private static final List<GroundServiceType> EXPECTED = List.of(
            GroundServiceType.HANDLING, GroundServiceType.FUEL);

    private final LegRepository legRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final PermitRequestRepository permitRequestRepository;
    private final GroundServiceService groundServiceService;
    private final SupplierRepository supplierRepository;
    private final TripSupportMapper mapper;

    public TripSupportBoardServiceImpl(LegRepository legRepository,
                                       ServiceRequestRepository serviceRequestRepository,
                                       PermitRequestRepository permitRequestRepository,
                                       GroundServiceService groundServiceService,
                                       SupplierRepository supplierRepository,
                                       TripSupportMapper mapper) {
        this.legRepository = legRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.permitRequestRepository = permitRequestRepository;
        this.groundServiceService = groundServiceService;
        this.supplierRepository = supplierRepository;
        this.mapper = mapper;
    }

    @Override
    public TripSupportBoardDto findBoard(UUID tenantId, LocalDate date) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Leg> legs = legRepository.findProgramme(tenantId, start, start.plusDays(1));

        if (legs.isEmpty()) {
            return new TripSupportBoardDto(date, List.of(), 0, 0, 0, 0, 0, now);
        }

        List<UUID> legIds = legs.stream().map(Leg::getId).toList();

        Map<UUID, List<ServiceRequestDto>> servicesByLeg = new HashMap<>();
        Map<UUID, Set<String>> requestedTypes = new HashMap<>();
        for (ServiceRequest request : serviceRequestRepository.findByLegIds(tenantId, legIds)) {
            servicesByLeg.computeIfAbsent(request.getLegId(), key -> new ArrayList<>())
                    .add(mapper.toDto(request));
            requestedTypes.computeIfAbsent(request.getLegId(), key -> new HashSet<>())
                    .add(request.getServiceType().name());
        }

        Map<UUID, List<PermitRequestDto>> permitsByLeg = new HashMap<>();
        Map<UUID, Integer> permitsOutstandingByLeg = new HashMap<>();
        for (PermitRequest request : permitRequestRepository.findByLegIds(tenantId, legIds)) {
            permitsByLeg.computeIfAbsent(request.getLegId(), key -> new ArrayList<>())
                    .add(mapper.toDto(request));
            if (request.getStatus() != RequestStatus.CONFIRMED) {
                permitsOutstandingByLeg.merge(request.getLegId(), 1, Integer::sum);
            }
        }

        Map<UUID, LegServicesSummary> readiness = groundServiceService.summariseByLegIds(tenantId, legIds);

        // Lead times per station, in one query: "too late to request" is a stored
        // notice period, not a rule of thumb.
        Set<String> stations = new HashSet<>();
        legs.forEach(leg -> {
            stations.add(leg.getDepIcao());
            stations.add(leg.getArrIcao());
        });
        Map<String, Integer> leadTime = new HashMap<>();
        for (Supplier supplier : supplierRepository.findForStations(tenantId, stations)) {
            if (supplier.isPreferred() && supplier.getLeadTimeHours() != null) {
                leadTime.merge(supplier.getStationIcao() + "|" + supplier.getServiceType().name(),
                        supplier.getLeadTimeHours(), Math::max);
            }
        }

        List<TripSupportRowDto> rows = new ArrayList<>(legs.size());
        int fullyServiced = 0;
        int pending = 0;
        int outstandingPermits = 0;
        int atRisk = 0;

        for (Leg leg : legs) {
            LegServicesSummary summary = readiness.getOrDefault(
                    leg.getId(), LegServicesSummary.nothingRequested(leg.getId()));
            Set<String> requested = requestedTypes.getOrDefault(leg.getId(), Set.of());

            List<String> missing = EXPECTED.stream()
                    .map(Enum::name)
                    .filter(type -> !requested.contains(type))
                    .toList();

            boolean risk = false;
            for (String type : missing) {
                Integer notice = leadTime.get(leg.getDepIcao() + "|" + type);
                if (notice != null && Duration.between(now, leg.getStd()).toHours() < notice) {
                    risk = true;
                }
            }

            int legPermitsOutstanding = permitsOutstandingByLeg.getOrDefault(leg.getId(), 0);
            if (summary.total() > 0 && summary.total() == summary.confirmed() && missing.isEmpty()) {
                fullyServiced++;
            }
            pending += summary.total() - summary.confirmed();
            outstandingPermits += legPermitsOutstanding;
            if (risk) {
                atRisk++;
            }

            List<PermitRequestDto> permits = permitsByLeg.getOrDefault(leg.getId(), List.of());
            rows.add(new TripSupportRowDto(
                    leg.getId(),
                    leg.getFlightNo(),
                    leg.getAircraft().getRegistration(),
                    leg.getDepIcao(),
                    leg.getArrIcao(),
                    leg.getStd(),
                    leg.getSta(),
                    leg.getStatus().name(),
                    summary.total(),
                    summary.confirmed(),
                    summary.readiness(),
                    servicesByLeg.getOrDefault(leg.getId(), List.of()),
                    missing,
                    permits.size(),
                    legPermitsOutstanding,
                    false,
                    permits,
                    risk));
        }

        return new TripSupportBoardDto(date, rows, legs.size(), fullyServiced,
                pending, outstandingPermits, atRisk, now);
    }

    @Override
    public List<SupplierDto> findSuppliers(UUID tenantId, String stationIcao) {
        String station = (stationIcao == null || stationIcao.isBlank())
                ? null
                : stationIcao.trim().toUpperCase();
        return supplierRepository.findDirectory(tenantId, station).stream()
                .map(this::toDto)
                .toList();
    }

    private SupplierDto toDto(Supplier supplier) {
        return new SupplierDto(
                supplier.getId(),
                supplier.getStationIcao(),
                supplier.getServiceType().name(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getSita(),
                supplier.getContractRef(),
                supplier.isPreferred(),
                supplier.getLeadTimeHours(),
                supplier.getRemark());
    }
}
