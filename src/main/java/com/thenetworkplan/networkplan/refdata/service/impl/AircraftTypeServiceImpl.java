package com.thenetworkplan.networkplan.refdata.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.refdata.dto.AircraftTypeDto;
import com.thenetworkplan.networkplan.refdata.mapper.RefDataMapper;
import com.thenetworkplan.networkplan.refdata.repository.AircraftTypeRepository;
import com.thenetworkplan.networkplan.refdata.service.AircraftTypeService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AircraftTypeServiceImpl implements AircraftTypeService {

    private final AircraftTypeRepository aircraftTypeRepository;
    private final RefDataMapper mapper;

    public AircraftTypeServiceImpl(AircraftTypeRepository aircraftTypeRepository, RefDataMapper mapper) {
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.AIRCRAFT_TYPES, key = "#icaoType")
    public AircraftTypeDto findByIcaoType(String icaoType) {
        return aircraftTypeRepository.findByIcaoType(icaoType)
                .map(mapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft type", icaoType));
    }
}
