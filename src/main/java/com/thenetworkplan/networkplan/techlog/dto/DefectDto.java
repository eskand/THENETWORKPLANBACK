package com.thenetworkplan.networkplan.techlog.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DefectDto(
        UUID id,
        UUID aircraftId,
        String registration,
        UUID techLogEntryId,
        String ataChapter,
        String description,
        OffsetDateTime reportedAt,
        String reportedByName,
        String status,
        UUID melItemId,
        String correctiveAction,
        OffsetDateTime closedAt) implements Serializable {
}
