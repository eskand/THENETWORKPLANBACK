package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestDto(
        UUID id,
        UUID legId,
        String stationIcao,
        String serviceType,
        String supplierName,
        String status,
        String reference,
        OffsetDateTime sentAt,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime confirmedAt,
        String remark) implements Serializable {
}
