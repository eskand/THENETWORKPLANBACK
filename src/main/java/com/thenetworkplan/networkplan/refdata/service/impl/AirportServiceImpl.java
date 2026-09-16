package com.thenetworkplan.networkplan.refdata.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.mapper.RefDataMapper;
import com.thenetworkplan.networkplan.refdata.repository.AirportRepository;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;
    private final RefDataMapper mapper;

    public AirportServiceImpl(AirportRepository airportRepository, RefDataMapper mapper) {
        this.airportRepository = airportRepository;
        this.mapper = mapper;
    }

    /**
     * Cached for twelve hours: an aerodrome record changes on an AIP amendment,
     * not during a shift.
     */
    @Override
    @Cacheable(cacheNames = CacheNames.AIRPORTS, key = "#icao")
    public AirportDto findByIcao(String icao) {
        return airportRepository.findByIcao(icao)
                .map(mapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("Airport", icao));
    }

    /**
     * Not cached on purpose: this is a single indexed {@code IN} query used by the
     * dispatch board, and the board itself is the cached read model.
     */
    @Override
    public Map<String, AirportDto> findAllByIcao(Collection<String> icaoCodes) {
        if (icaoCodes == null || icaoCodes.isEmpty()) {
            return Map.of();
        }
        Set<String> distinct = icaoCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<AirportDto> found = airportRepository.findByIcaoIn(distinct).stream()
                .map(mapper::toDto)
                .toList();
        Map<String, AirportDto> byIcao = new LinkedHashMap<>();
        for (AirportDto dto : found) {
            byIcao.put(dto.icao(), dto);
        }
        return byIcao;
    }
}
