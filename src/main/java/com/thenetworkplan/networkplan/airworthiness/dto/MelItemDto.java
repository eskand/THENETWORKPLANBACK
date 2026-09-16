package com.thenetworkplan.networkplan.airworthiness.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MelItemDto(
        UUID id,
        String reference,
        String melCategory,
        String title,
        String limitation,
        OffsetDateTime raisedAt,
        OffsetDateTime dueAt,
        boolean blocksDispatch) implements Serializable {
}
