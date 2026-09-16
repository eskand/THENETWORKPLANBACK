package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A leg as DOM1 exposes it. Station codes are ICAO only: translating to the IATA
 * code an operator reads is the presentation layer's job, and it needs the
 * reference set to do it.
 */
public record LegDto(
        UUID id,
        UUID tripId,
        String clientRef,
        String flightNo,
        UUID aircraftId,
        String registration,
        String icaoType,
        String model,
        String aircraftStatus,
        String depIcao,
        String arrIcao,
        String baseIcao,
        OffsetDateTime std,
        OffsetDateTime sta,
        OffsetDateTime etd,
        OffsetDateTime eta,
        OffsetDateTime outAt,
        OffsetDateTime offAt,
        OffsetDateTime onAt,
        OffsetDateTime inAt,
        OffsetDateTime ctot,
        String status,
        String flightType,
        int paxCount,
        String riskLevel,
        OffsetDateTime mvtSentAt,
        String remark,
        String businessKey) implements Serializable {
}
