package com.thenetworkplan.networkplan.refdata.mapper;

import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.refdata.domain.Airport;
import com.thenetworkplan.networkplan.refdata.dto.AircraftTypeDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO translation for the reference-data module.
 *
 * <p>Written by hand rather than generated: mapping is the one place where the
 * wire contract is decided, and it should be readable in a pull request.
 */
@Component
public class RefDataMapper {

    public AirportDto toDto(Airport entity) {
        return new AirportDto(
                entity.getIcao(),
                entity.getIata(),
                entity.getName(),
                entity.getCity(),
                entity.getCountryIso2(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getElevationFt(),
                entity.getLongestRunwayFt(),
                entity.getAerodromeCategory(),
                entity.getRffsCategory(),
                entity.getTimeZone(),
                entity.getRegion(),
                entity.getTrafficLevel(),
                entity.getFuelType(),
                entity.getFireCategory(),
                entity.getOperatingHours(),
                entity.isSlotRequired(),
                entity.getSlotRegime(),
                entity.getRestrictions(),
                entity.getRunwayRemark());
    }

    public AircraftTypeDto toDto(AircraftType entity) {
        return new AircraftTypeDto(
                entity.getIcaoType(),
                entity.getManufacturer(),
                entity.getModel(),
                entity.getWakeCategory(),
                entity.getMtowKg(),
                entity.getMinRunwayFt(),
                entity.getMaxPax(),
                entity.getRangeNm(),
                entity.getCruiseTasKt(),
                entity.isEtopsApplicable());
    }
}
