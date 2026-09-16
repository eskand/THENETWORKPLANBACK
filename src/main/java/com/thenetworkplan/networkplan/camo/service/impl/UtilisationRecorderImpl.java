package com.thenetworkplan.networkplan.camo.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.camo.domain.Utilisation;
import com.thenetworkplan.networkplan.camo.repository.UtilisationRepository;
import com.thenetworkplan.networkplan.camo.service.UtilisationRecorder;
import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UtilisationRecorderImpl implements UtilisationRecorder {

    private final UtilisationRepository utilisationRepository;
    private final AircraftRepository aircraftRepository;

    public UtilisationRecorderImpl(UtilisationRepository utilisationRepository,
                                   AircraftRepository aircraftRepository) {
        this.utilisationRepository = utilisationRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLEET, key = "#tenantId"),
            @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    })
    public boolean record(UUID tenantId,
                          Aircraft aircraft,
                          UUID legId,
                          LocalDate flownOn,
                          int blockMinutes,
                          Integer airMinutes,
                          int cycles,
                          String reference) {

        if (legId != null && utilisationRepository.findByTenantIdAndLegId(tenantId, legId).isPresent()) {
            // Idempotent on the leg: the unique constraint says the same thing,
            // and this check turns a 500 into a plain "already recorded".
            return false;
        }

        Utilisation utilisation = new Utilisation();
        utilisation.setTenantId(tenantId);
        utilisation.setAircraft(aircraft);
        utilisation.setLegId(legId);
        utilisation.setFlownOn(flownOn);
        utilisation.setBlockMinutes(Math.max(0, blockMinutes));
        utilisation.setAirMinutes(airMinutes);
        utilisation.setCycles(Math.max(0, cycles));
        utilisation.setSource(Source.of("techlog", reference));
        utilisationRepository.save(utilisation);

        // The counters move here and nowhere else.
        BigDecimal hours = BigDecimal.valueOf(utilisation.getBlockMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal currentHours = aircraft.getHoursSinceNew() == null
                ? BigDecimal.ZERO
                : aircraft.getHoursSinceNew();
        int currentCycles = aircraft.getCyclesSinceNew() == null ? 0 : aircraft.getCyclesSinceNew();

        aircraft.setHoursSinceNew(currentHours.add(hours));
        aircraft.setCyclesSinceNew(currentCycles + utilisation.getCycles());
        aircraftRepository.save(aircraft);
        return true;
    }
}
