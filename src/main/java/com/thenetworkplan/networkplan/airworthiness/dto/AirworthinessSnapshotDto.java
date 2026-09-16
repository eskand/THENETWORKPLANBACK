package com.thenetworkplan.networkplan.airworthiness.dto;

import java.io.Serializable;
import java.util.List;

/**
 * What dispatch needs to know about a tail before releasing a flight: the status,
 * the open MEL items and whether any of them forbids the flight.
 */
public record AirworthinessSnapshotDto(
        AircraftDto aircraft,
        List<MelItemDto> openMelItems,
        boolean blockedByMel) implements Serializable {

    public boolean releasable() {
        return "SERVICEABLE".equals(aircraft.status()) && !blockedByMel;
    }
}
