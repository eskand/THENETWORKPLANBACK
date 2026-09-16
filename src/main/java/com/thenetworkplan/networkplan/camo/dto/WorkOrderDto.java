package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One work order raised against a registration.
 *
 * <p>{@code overdue} is computed from the target date and the day asked about,
 * never read from a column: an order stops being late when it closes.
 */
public record WorkOrderDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String orderNo,
        String title,
        String status,
        String facility,
        String facilityIcao,
        LocalDate openedOn,
        LocalDate targetOn,
        String targetNote,
        LocalDate closedOn,
        BigDecimal labourHours,
        boolean overdue) implements Serializable {
}
