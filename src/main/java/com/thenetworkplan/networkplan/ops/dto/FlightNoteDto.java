package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** La note d'exploitation, avec quand et par qui — l'annexe n'avait que quand. */
public record FlightNoteDto(
        UUID legId,
        String note,
        OffsetDateTime noteAt,
        UUID noteBy) implements Serializable {
}
