package com.thenetworkplan.networkplan.airworthiness.mapper;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import org.springframework.stereotype.Component;

@Component
public class AirworthinessMapper {

    /** Expects {@code aircraftType} to be loaded: every query fetch-joins it. */
    public AircraftDto toDto(Aircraft entity) {
        return new AircraftDto(
                entity.getId(),
                entity.getRegistration(),
                entity.getAircraftType().getIcaoType(),
                entity.getAircraftType().getModel(),
                entity.getAircraftType().getMaxPax(),
                entity.getAircraftType().getMinRunwayFt(),
                entity.getHomeBaseIcao(),
                entity.getCurrentBaseIcao(),
                entity.getStatus().name(),
                entity.getStatusReason(),
                entity.getStatusSince(),
                entity.getHoursSinceNew(),
                entity.getCyclesSinceNew(),
                entity.getNextCheckLabel(),
                entity.getNextCheckDueAt());
    }

    public MelItemDto toDto(MelItem entity) {
        return new MelItemDto(
                entity.getId(),
                entity.getReference(),
                entity.getMelCategory().name(),
                entity.getTitle(),
                entity.getLimitation(),
                entity.getRaisedAt(),
                entity.getDueAt(),
                entity.isBlocksDispatch());
    }
}
