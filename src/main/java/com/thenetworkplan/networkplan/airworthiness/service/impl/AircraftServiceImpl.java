package com.thenetworkplan.networkplan.airworthiness.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.AirworthinessSnapshotDto;
import com.thenetworkplan.networkplan.airworthiness.dto.ChangeAircraftStatusCommand;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.mapper.AirworthinessMapper;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.repository.MelItemRepository;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AircraftServiceImpl implements AircraftService {

    private final AircraftRepository aircraftRepository;
    private final MelItemRepository melItemRepository;
    private final AirworthinessMapper mapper;

    public AircraftServiceImpl(AircraftRepository aircraftRepository,
                               MelItemRepository melItemRepository,
                               AirworthinessMapper mapper) {
        this.aircraftRepository = aircraftRepository;
        this.melItemRepository = melItemRepository;
        this.mapper = mapper;
    }

    /**
     * Cached for five minutes only: the fleet list carries the airworthiness
     * status, and an AOG has to reach the board within seconds, not minutes.
     * Every status change evicts this entry, so the TTL is a safety net.
     */
    @Override
    @Cacheable(cacheNames = CacheNames.FLEET, key = "#tenantId")
    public List<AircraftDto> findFleet(UUID tenantId) {
        return aircraftRepository.findFleet(tenantId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<AircraftDto> findGrounded(UUID tenantId) {
        return aircraftRepository
                .findFleetByStatus(tenantId, EnumSet.of(AircraftStatus.AOG, AircraftStatus.MAINTENANCE))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public AirworthinessSnapshotDto findAirworthiness(UUID tenantId, UUID aircraftId) {
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, aircraftId)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", aircraftId));
        List<MelItem> open = melItemRepository.findOpenForAircraft(tenantId, aircraftId);
        List<MelItemDto> items = open.stream().map(mapper::toDto).toList();
        boolean blocked = open.stream().anyMatch(MelItem::isBlocksDispatch);
        return new AirworthinessSnapshotDto(mapper.toDto(aircraft), items, blocked);
    }

    @Override
    public Map<UUID, List<MelItemDto>> findOpenMelByAircraft(UUID tenantId) {
        Map<UUID, List<MelItemDto>> grouped = new LinkedHashMap<>();
        for (MelItem item : melItemRepository.findOpenForFleet(tenantId)) {
            grouped.computeIfAbsent(item.getAircraft().getId(), key -> new ArrayList<>())
                    .add(mapper.toDto(item));
        }
        return grouped;
    }

    /**
     * Writes evict both the fleet list and the rendered board: a stale AOG on an
     * OCC screen is exactly the class of defect the audit flagged.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLEET, key = "#tenantId"),
            @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    })
    public AircraftDto changeStatus(UUID tenantId, UUID aircraftId, ChangeAircraftStatusCommand command) {
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, aircraftId)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", aircraftId));

        AircraftStatus target;
        try {
            target = AircraftStatus.valueOf(command.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("AIRCRAFT_STATUS_UNKNOWN",
                    "Unknown aircraft status: " + command.status());
        }

        if (aircraft.getStatus() == target) {
            return mapper.toDto(aircraft);
        }
        aircraft.setStatus(target);
        aircraft.setStatusReason(command.reason());
        aircraft.setStatusSince(OffsetDateTime.now());
        return mapper.toDto(aircraftRepository.save(aircraft));
    }
}
