package com.thenetworkplan.networkplan.flightfollowing.mapper;

import com.thenetworkplan.networkplan.flightfollowing.domain.PositionReport;
import com.thenetworkplan.networkplan.flightfollowing.dto.PositionDto;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class FlightFollowingMapper {

    /** @param now the instant the whole board was computed against */
    public PositionDto toDto(PositionReport position, OffsetDateTime now) {
        return new PositionDto(
                position.getId(),
                position.getLegId(),
                position.getAircraft().getId(),
                position.getAircraft().getRegistration(),
                position.getReportedAt(),
                position.getReceivedAt(),
                Duration.between(position.getReportedAt(), now).toMinutes(),
                position.getLatitude(),
                position.getLongitude(),
                position.getAltitudeFt(),
                position.getGroundSpeedKt(),
                position.getTrackDeg(),
                position.getVerticalRateFpm(),
                position.getOnGround(),
                position.getProvider().name(),
                position.getProvider().isAutomatic(),
                position.getProviderRef());
    }
}
