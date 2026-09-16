package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One leg on the NetPlus Services board.
 *
 * <p>{@code missingServices} is the actionable part: the services the operator
 * expects at that station and which have no request yet. It is a difference
 * between two stored sets, not a guess.
 */
public record TripSupportRowDto(
        UUID legId,
        String flightNo,
        String registration,
        String depIcao,
        String arrIcao,
        OffsetDateTime std,
        OffsetDateTime sta,
        String status,
        int servicesTotal,
        int servicesConfirmed,
        String servicesReadiness,
        List<ServiceRequestDto> services,
        List<String> missingServices,
        int permitsTotal,
        int permitsOutstanding,
        boolean anyUnknownInstrument,
        List<PermitRequestDto> permits,
        /** True when a request still to be sent is already inside the lead time. */
        boolean leadTimeAtRisk) implements Serializable {
}
