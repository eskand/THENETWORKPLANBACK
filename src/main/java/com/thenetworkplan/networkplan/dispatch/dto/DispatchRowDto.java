package com.thenetworkplan.networkplan.dispatch.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One line of the dispatch board, flattened for the table.
 *
 * <p>Station codes come in both forms: {@code depIcao} is the key the rest of the
 * system uses, {@code depCode} is the IATA code an operator reads. The prototype
 * stored one field and put whichever it had into it.
 */
public record DispatchRowDto(
        DispatchRowKind kind,
        UUID rowId,
        UUID legId,
        UUID aircraftId,

        String flightNo,
        String label,
        String riskLevel,

        String registration,
        String icaoType,
        String model,

        String depIcao,
        String depCode,
        String arrIcao,
        String arrCode,
        String routeLabel,
        String baseIcao,

        OffsetDateTime std,
        OffsetDateTime etd,
        OffsetDateTime atd,
        OffsetDateTime sta,
        OffsetDateTime eta,
        OffsetDateTime ata,
        OffsetDateTime ctot,

        String servicesReadiness,
        int servicesConfirmed,
        int servicesTotal,

        int permitsOutstanding,

        boolean crewAssigned,
        int crewSeatsFilled,
        int crewMinimumSeats,
        String crewFtlStatus,
        String crewDocumentStatus,

        String status,
        String statusTone,
        boolean attention,
        boolean melBlocking,
        int delayMinutes,
        String note) implements Serializable {
}
