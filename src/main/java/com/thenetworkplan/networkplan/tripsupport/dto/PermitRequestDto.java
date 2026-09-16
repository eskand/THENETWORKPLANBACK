package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PermitRequestDto(
        UUID id,
        UUID legId,
        String countryIso2,
        String kind,
        String status,
        String recipient,
        String reference,
        OffsetDateTime sentAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validTo) implements Serializable {
}
