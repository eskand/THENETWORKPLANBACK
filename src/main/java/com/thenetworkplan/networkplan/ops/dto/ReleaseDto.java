package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReleaseDto(
        UUID id,
        UUID legId,
        int version,
        UUID signedBy,
        OffsetDateTime signedAt,
        boolean derogation,
        String derogationReason,
        UUID captainAckBy,
        OffsetDateTime captainAckAt,
        boolean acknowledged) implements Serializable {
}
