package com.thenetworkplan.networkplan.timeline.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * One lane: a registration and everything it does in the window.
 *
 * @param fleetSection the heading the lane sits under — "Falcon Fleet",
 *                     "Citation 525-Family Fleet", and so on. Derived from the
 *                     aircraft type by {@code FleetSection}, in one place, so
 *                     the Timeline and the fleet filter always agree
 * @param model        the full model name, shown as the tooltip of the ICAO code
 */
public record TimelineRowDto(
        UUID aircraftId,
        String registration,
        String icaoType,
        String model,
        String fleetSection,
        String baseIcao,
        String status,
        String statusReason,
        List<TimelineSegmentDto> segments,
        int flights,
        long blockMinutes,
        long groundMinutes,
        int tightTurnarounds) implements Serializable {
}
