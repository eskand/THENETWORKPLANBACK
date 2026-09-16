package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** API5 — permit picture of a leg: verdict per state, plus the requests. */
public record LegPermitsDto(
        UUID legId,
        List<CountryStatusDto> countries,
        List<PermitRequestDto> requests,
        int outstandingCount,
        boolean anyUnknownInstrument) implements Serializable {
}
