package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertDto(
        UUID id,
        UUID legId,
        String severity,
        String rule,
        String cause,
        String targetRole,
        OffsetDateTime raisedAt) implements Serializable {
}
