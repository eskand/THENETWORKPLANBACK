package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One life-limited part on the fleet.
 *
 * <p>{@code percentRemaining} is derived from the limit and the consumption on
 * the day the row is built, and rounded once, here. The tab sorts on it and
 * the gauge draws it, and both use the same number because there is only one.
 *
 * <p>{@code governedBy} names which of the three possible limits is the nearest
 * — CYCLES, HOURS or MONTHS. Without it a part at eight per cent gives no clue
 * whether flying less would help.
 */
public record LifeLimitedPartDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String icaoType,
        String name,
        String partNo,
        String serialNo,
        String position,
        Integer limitCycles,
        Integer usedCycles,
        Integer cyclesRemaining,
        Integer limitMonths,
        LocalDate installedOn,
        int percentRemaining,
        String governedBy,
        String severity) implements Serializable {
}
