package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** Read model of an aerodrome. Serializable because it is cached in Redis. */
public record AirportDto(
        String icao,
        String iata,
        String name,
        String city,
        String countryIso2,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer elevationFt,
        Integer longestRunwayFt,
        String aerodromeCategory,
        String rffsCategory,
        String timeZone,
        /* --- l annuaire --- */
        Short region,
        String trafficLevel,
        String fuelType,
        String fireCategory,
        String operatingHours,
        boolean slotRequired,
        String slotRegime,
        String restrictions,
        String runwayRemark) implements Serializable {

    /** Code an operator reads on a strip: IATA when published, ICAO otherwise. */
    public String displayCode() {
        return iata == null || iata.isBlank() ? icao : iata;
    }
}
