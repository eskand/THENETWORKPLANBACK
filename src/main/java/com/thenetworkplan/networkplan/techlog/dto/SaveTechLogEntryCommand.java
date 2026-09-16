package com.thenetworkplan.networkplan.techlog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Opening or amending a tech log page.
 *
 * <p>Times are minutes, not decimal hours: the audit found the prototype
 * storing decimal hours with no date, and a block time of 1.75 is where that
 * starts.
 */
public record SaveTechLogEntryCommand(
        @NotNull UUID aircraftId,
        UUID legId,
        @NotNull LocalDate flownOn,
        String depIcao,
        String arrIcao,
        @Min(0) Integer blockMinutes,
        @Min(0) Integer airMinutes,
        @Min(0) Integer cycles,
        BigDecimal fuelUpliftLitres,
        BigDecimal oilAddedLitres,
        UUID commanderId,
        UUID engineerId,
        String remark) {
}
