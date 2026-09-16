package com.thenetworkplan.networkplan.tripsupport.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.tripsupport.domain.CountryPermitStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.CountryStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.PermitKind;
import com.thenetworkplan.networkplan.tripsupport.domain.PermitRequest;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.dto.CountryStatusDto;
import com.thenetworkplan.networkplan.tripsupport.dto.CreatePermitRequestCommand;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsDto;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegRequestCount;
import com.thenetworkplan.networkplan.tripsupport.dto.PermitRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.UpdateRequestStatusCommand;
import com.thenetworkplan.networkplan.tripsupport.mapper.TripSupportMapper;
import com.thenetworkplan.networkplan.tripsupport.repository.CountryStatusRepository;
import com.thenetworkplan.networkplan.tripsupport.repository.PermitRequestRepository;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import com.thenetworkplan.networkplan.tripsupport.service.RequestStatusTransition;
import java.time.OffsetDateTime;
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
public class PermitServiceImpl implements PermitService {

    private final PermitRequestRepository permitRepository;
    private final CountryStatusRepository countryStatusRepository;
    private final RequestStatusTransition transition;
    private final TripSupportMapper mapper;

    public PermitServiceImpl(PermitRequestRepository permitRepository,
                             CountryStatusRepository countryStatusRepository,
                             RequestStatusTransition transition,
                             TripSupportMapper mapper) {
        this.permitRepository = permitRepository;
        this.countryStatusRepository = countryStatusRepository;
        this.transition = transition;
        this.mapper = mapper;
    }

    @Override
    public Map<UUID, LegPermitsSummary> summariseByLegIds(UUID tenantId, Collection<UUID> legIds) {
        if (legIds == null || legIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, int[]> tally = new LinkedHashMap<>();
        for (LegRequestCount row : permitRepository.countByLegAndStatus(tenantId, legIds)) {
            int count = row.count() == null ? 0 : row.count().intValue();
            int[] counters = tally.computeIfAbsent(row.legId(), key -> new int[2]);
            counters[0] += count;
            if (row.status().isOutstanding()) {
                counters[1] += count;
            }
        }
        Map<UUID, LegPermitsSummary> summaries = new LinkedHashMap<>();
        tally.forEach((legId, counters) ->
                summaries.put(legId, new LegPermitsSummary(legId, counters[0], counters[1])));
        return summaries;
    }

    @Override
    public long countOutstanding(UUID tenantId, Collection<UUID> legIds) {
        if (legIds == null || legIds.isEmpty()) {
            return 0L;
        }
        return permitRepository.countOutstanding(tenantId, legIds, RequestStatus.CONFIRMED);
    }

    @Override
    public LegPermitsDto findByLeg(UUID tenantId, UUID legId) {
        List<CountryStatus> countries = countryStatusRepository.findByLeg(tenantId, legId);
        List<PermitRequest> requests = permitRepository.findByLeg(tenantId, legId);

        List<CountryStatusDto> countryDtos = countries.stream().map(mapper::toDto).toList();
        List<PermitRequestDto> requestDtos = requests.stream().map(mapper::toDto).toList();

        int outstanding = (int) requests.stream()
                .filter(request -> request.getStatus().isOutstanding())
                .count();
        boolean anyUnknown = countries.stream()
                .anyMatch(country -> country.getStatus() == CountryPermitStatus.NO_INSTRUMENT_KNOWN);

        return new LegPermitsDto(legId, countryDtos, requestDtos, outstanding, anyUnknown);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public PermitRequestDto create(UUID tenantId, UUID legId, CreatePermitRequestCommand command) {
        PermitKind kind;
        try {
            kind = PermitKind.valueOf(command.kind().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("PERMIT_KIND_UNKNOWN", "Unknown permit kind: " + command.kind());
        }

        PermitRequest request = new PermitRequest();
        request.setTenantId(tenantId);
        request.setLegId(legId);
        request.setCountryIso2(command.countryIso2().trim().toUpperCase());
        request.setKind(kind);
        request.setRecipient(command.recipient());
        request.setStatus(RequestStatus.DRAFT);
        return mapper.toDto(permitRepository.save(request));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public PermitRequestDto updateStatus(UUID tenantId, UUID requestId, UpdateRequestStatusCommand command) {
        PermitRequest request = permitRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Permit request", requestId));

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
        request.setStatus(target);
        return mapper.toDto(permitRepository.save(request));
    }

    @Override
    public java.util.List<PermitRequestDto> findOutstandingInWindow(UUID tenantId,
                                                                    java.time.LocalDate from,
                                                                    java.time.LocalDate to) {
        return permitRepository.findOutstandingInWindow(
                        tenantId,
                        from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
                        to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC))
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
