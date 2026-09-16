package com.thenetworkplan.networkplan.tripsupport.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.tripsupport.domain.GroundServiceType;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.ServiceRequest;
import com.thenetworkplan.networkplan.tripsupport.dto.CreateServiceRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceReadiness;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import com.thenetworkplan.networkplan.tripsupport.mapper.TripSupportMapper;
import com.thenetworkplan.networkplan.tripsupport.repository.ServiceRequestRepository;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.RequestStatusTransition;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroundServiceServiceImpl implements GroundServiceService {

    private final ServiceRequestRepository requestRepository;
    private final RequestStatusTransition transition;
    private final TripSupportMapper mapper;

    public GroundServiceServiceImpl(ServiceRequestRepository requestRepository,
                                    RequestStatusTransition transition,
                                    TripSupportMapper mapper) {
        this.requestRepository = requestRepository;
        this.transition = transition;
        this.mapper = mapper;
    }

    @Override
    public Map<UUID, LegServicesSummary> summariseByLegIds(UUID tenantId, Collection<UUID> legIds) {
        if (legIds == null || legIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, int[]> tally = new LinkedHashMap<>();
        Map<UUID, Boolean> refused = new LinkedHashMap<>();

        for (LegRequestCount row : requestRepository.countByLegAndStatus(tenantId, legIds)) {
            int count = row.count() == null ? 0 : row.count().intValue();
            int[] counters = tally.computeIfAbsent(row.legId(), key -> new int[2]);
            counters[0] += count;
            if (row.status() == RequestStatus.CONFIRMED) {
                counters[1] += count;
            }
            if (row.status() == RequestStatus.REFUSED) {
                refused.put(row.legId(), Boolean.TRUE);
            }
        }

        Map<UUID, LegServicesSummary> summaries = new LinkedHashMap<>();
        tally.forEach((legId, counters) -> {
            ServiceReadiness readiness = ServiceReadiness.of(
                    counters[0], counters[1], refused.getOrDefault(legId, Boolean.FALSE));
            summaries.put(legId, new LegServicesSummary(legId, counters[0], counters[1], readiness.name()));
        });
        return summaries;
    }

    @Override
    public LegServicesDto findByLeg(UUID tenantId, UUID legId) {
        List<ServiceRequest> requests = requestRepository.findByLeg(tenantId, legId);
        List<ServiceRequestDto> dtos = new ArrayList<>(requests.size());
        int confirmed = 0;
        boolean refused = false;
        for (ServiceRequest request : requests) {
            dtos.add(mapper.toDto(request));
            if (request.getStatus() == RequestStatus.CONFIRMED) {
                confirmed++;
            }
            if (request.getStatus() == RequestStatus.REFUSED) {
                refused = true;
            }
        }
        ServiceReadiness readiness = ServiceReadiness.of(requests.size(), confirmed, refused);
        return new LegServicesDto(legId, List.copyOf(dtos), requests.size(), confirmed, readiness.name());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public ServiceRequestDto create(UUID tenantId, UUID legId, CreateServiceRequestCommand command) {
        GroundServiceType type = parseType(command.serviceType());
        String station = command.stationIcao().trim().toUpperCase();

        requestRepository
                .findByTenantIdAndLegIdAndStationIcaoAndServiceType(tenantId, legId, station, type)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("SERVICE_ALREADY_REQUESTED",
                            type + " is already requested at " + station + " for this leg");
                });

        ServiceRequest request = new ServiceRequest();
        request.setTenantId(tenantId);
        request.setLegId(legId);
        request.setStationIcao(station);
        request.setServiceType(type);
        request.setSupplierName(command.supplierName());
        request.setRemark(command.remark());
        request.setStatus(RequestStatus.DRAFT);
        return mapper.toDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public ServiceRequestDto updateStatus(UUID tenantId, UUID requestId, UpdateRequestStatusCommand command) {
        ServiceRequest request = requestRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Service request", requestId));

        RequestStatus target = transition.parse(command.status());
        transition.check(request.getStatus(), target, command.reference());

        OffsetDateTime now = OffsetDateTime.now();
        switch (target) {
            case SENT -> request.setSentAt(now);
            case ACKNOWLEDGED -> request.setAcknowledgedAt(now);
            case CONFIRMED -> {
                request.setConfirmedAt(now);
                request.setReference(command.reference());
            }
            case REFUSED -> request.setConfirmedAt(null);
            case DRAFT -> {
                // unreachable: the transition table has no path back to DRAFT
            }
        }
        if (command.remark() != null) {
            request.setRemark(command.remark());
        }
        request.setStatus(target);
        return mapper.toDto(requestRepository.save(request));
    }

    private GroundServiceType parseType(String raw) {
        try {
            return GroundServiceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("SERVICE_TYPE_UNKNOWN", "Unknown service type: " + raw);
        }
    }
}
